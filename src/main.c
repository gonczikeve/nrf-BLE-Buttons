
#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/sys/util.h>
#include <zephyr/logging/log.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/gap.h>
#include <zephyr/bluetooth/addr.h>

#include <zephyr/types.h>
#include <stddef.h>

#include <zephyr/sys/util.h>


#include <zephyr/bluetooth/hci.h>


LOG_MODULE_REGISTER(dynaforce, LOG_LEVEL_INF);
#define BUTTONS_NODE DT_PATH(buttons)
#define GPIO0_DEV DEVICE_DT_GET(DT_NODELABEL(gpio0))
#define GPIO1_DEV DEVICE_DT_GET(DT_NODELABEL(gpio1))

#define CODED_PHY 0//BT_LE_ADV_OPT_CODED //if we want to use coded phy

#define GPIO_SPEC_AND_COMMA(button) GPIO_DT_SPEC_GET(button, gpios),
static const struct gpio_dt_spec buttons[] = {
#if DT_NODE_EXISTS(BUTTONS_NODE)
	DT_FOREACH_CHILD(BUTTONS_NODE, GPIO_SPEC_AND_COMMA)
#endif
};



#define ADV_MIN 500/0.625
#define ADV_MAX 501/0.625
#define COMPANY_ID_CODE            0x0059//This is nordic's company ID, Algra Group should
//get their own company ID if using this solution


#define DEVICE_NAME "DynaForceButtons"
#define DEVICE_NAME_LEN (sizeof(DEVICE_NAME) - 1)
static struct bt_le_ext_adv *adv;

typedef struct __attribute__((packed)) message{
	uint8_t buttonstate;
	int32_t timestamp;
} message_t;

typedef struct fifo_message{
	void *fifo_reserved;    /* 1st word reserved for use by fifo */
	message_t message;
} fifo_message_t;

typedef struct adv_mfg_data {
	uint16_t company_code;	    /* Company Identifier Code. */
	message_t message;      
} adv_mfg_data_type;

static adv_mfg_data_type adv_mfg_data = {COMPANY_ID_CODE,0,0};

static const struct bt_data ad[] = {
	BT_DATA_BYTES(BT_DATA_FLAGS, BT_LE_AD_NO_BREDR),
	BT_DATA(BT_DATA_NAME_COMPLETE, DEVICE_NAME, DEVICE_NAME_LEN),
	BT_DATA(BT_DATA_MANUFACTURER_DATA,&adv_mfg_data, sizeof(adv_mfg_data))
	
};

struct k_fifo bp_fifo;



static const struct bt_data sd[] = {
	BT_DATA(BT_DATA_MANUFACTURER_DATA,&adv_mfg_data, sizeof(adv_mfg_data))
};

static struct bt_le_adv_param *adv_param =
	BT_LE_ADV_PARAM(BT_LE_ADV_OPT_USE_IDENTITY,
	ADV_MIN,
	ADV_MAX,
	NULL);

static struct gpio_callback button_cb_port0;
static struct gpio_callback button_cb_port1;

uint32_t pinmask_port0 = 0;
uint32_t pinmask_port1 = 0;

//semaphore to signal that a message has been received
static struct k_sem message_received;


void button_thread(void);
uint8_t buttonstateToUint(uint32_t buttonstate, uint32_t buttonstate2);
K_THREAD_DEFINE(button_thread_id, 1024, button_thread, NULL, NULL, NULL, 7, 0, 1000);

void advertisement_sent_cb(struct bt_le_ext_adv *instance,
			   struct bt_le_ext_adv_sent_info *info)
{
	LOG_INF("Advertised with new message %d times", info->num_sent);
	k_sem_give(&message_received);
}

struct bt_le_ext_adv_cb adv_cb = {
	.sent = advertisement_sent_cb,
};

void button_pressed_cb(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	message_t to_send;
	to_send.timestamp = (uint32_t)k_uptime_get();
	to_send.buttonstate = buttonstateToUint(pins, 0);

	fifo_message_t * fifo_message = k_malloc(sizeof(fifo_message_t));
	fifo_message->message = to_send;
	k_fifo_put(&bp_fifo, fifo_message);
	LOG_INF("callback at %d, ----------------------%d\n", to_send.timestamp,to_send.buttonstate);
}

void button_pressed_cb2(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	
	message_t to_send;
	
	to_send.timestamp = (uint32_t)k_uptime_get();
	to_send.buttonstate = buttonstateToUint(0, pins);
	fifo_message_t * fifo_message = k_malloc(sizeof(fifo_message_t));
	fifo_message->message = to_send;
	k_fifo_put(&bp_fifo, fifo_message);
	LOG_INF("callback2 at %d, ----------------------%d\n", to_send.timestamp,to_send.buttonstate);
}






int buttons_init(void){
	
	for(int i = 0; i < ARRAY_SIZE(buttons); i++){
		if(buttons[i].port == GPIO0_DEV){
			pinmask_port0 |= BIT(buttons[i].pin);
		}
		else if(buttons[i].port == GPIO1_DEV){
			pinmask_port1 |= BIT(buttons[i].pin);
		}
		else{
			LOG_ERR("Button %s pin %d not on GPIO0 or GPIO1", buttons[i].port->name, buttons[i].pin);
		}
	}

	gpio_init_callback(&button_cb_port0, button_pressed_cb, pinmask_port0);
	gpio_init_callback(&button_cb_port1, button_pressed_cb2, pinmask_port1);
	int ret;
	// Check if all buttons are ready
	for (int i=0; i < ARRAY_SIZE(buttons); i++) {
		if (!gpio_is_ready_dt(&buttons[i])) {
		LOG_ERR("Error: button device %s is not ready\n",
		       buttons[i].port->name);
		return 0;
		}
		else{
			LOG_INF("Button device %s is ready",
		       buttons[i].port->name);
		}
	}
	// Configure all buttons as inputs
	for (int i = 0; i < ARRAY_SIZE(buttons); i++) {
		ret = gpio_pin_configure_dt(&buttons[i], GPIO_INPUT);
		if (ret != 0) {
			LOG_ERR("Error %d: failed to configure %s pin %d\n",
				ret, buttons[i].port->name, buttons[i].pin);
			return ret;
		}
		else{
			LOG_INF("Button %s pin %d configured as input",
				buttons[i].port->name, buttons[i].pin);
		}
	}

	for(int i = 0; i < ARRAY_SIZE(buttons); i++){
		ret = gpio_pin_interrupt_configure_dt(&buttons[i],
					      GPIO_INT_EDGE_TO_ACTIVE);
		if (ret != 0) {
			LOG_ERR("Error %d: failed to configure interrupt on %s pin %d",
				ret, buttons[i].port->name, buttons[i].pin);
			return 0;
		}
		if(buttons[i].port == GPIO0_DEV){
			gpio_add_callback(buttons[i].port, &button_cb_port0);
		}
		else if(buttons[i].port == GPIO1_DEV){
			gpio_add_callback(buttons[i].port, &button_cb_port1);
		}
		else{
			LOG_ERR("Button %s pin %d not on GPIO0 or GPIO1", buttons[i].port->name, buttons[i].pin);
		}
		LOG_INF("Set up interrupt at %s pin %d", buttons[i].port->name, buttons[i].pin);
	}



	return 0;
}




uint8_t buttonstateToUint(uint32_t buttonstate, uint32_t buttonstate2){
	//pack the 8 buttons into a uint8_t
	uint8_t ret = 0;
	int ctr = 0;
	//check buttons on the first port
	for(int i = 0; i < 32; i++){
		if(pinmask_port0 & (1 << i)){
			ret |= ((buttonstate >> i) & 1) << ctr;
			ctr++;
			if(ctr > 4){
				LOG_ERR("More buttons than attached to the port are pressed, buttonstate: 0x%x, ret 0x%x", buttonstate, ret);
				break;
			}
		}
	}
	//check buttons on the second port
	ctr = 0;
	for(int i = 0; i < 32; i++){
		if(pinmask_port1 & (1 << i)){
			ret |= ((buttonstate2 >> i) & 1) << (ctr + 4);
			ctr++;
			if(ctr > 4){
				LOG_ERR("More buttons than attached to the port are pressed, buttonstate2: 0x%x, ret 0x%x", buttonstate2, ret);
				break;
			}
		}
	}

	return ret;
}

void button_thread(void){
	LOG_INF("Button thread started");
	int err;
	fifo_message_t *fifo_rec;

	
	while(1){
		LOG_INF("Blocking on fifo");
		//get message from fifo
	    fifo_rec = k_fifo_get(&bp_fifo, K_FOREVER);
		adv_mfg_data.message = fifo_rec->message;
		LOG_INF("Message received from fifo, %d, %d", fifo_rec->message.buttonstate, fifo_rec->message.timestamp);
		k_free(fifo_rec);
		err = bt_le_ext_adv_set_data(adv, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
		if (err) {
			LOG_ERR("Failed to set advertising data (%d)\n", err);
			return -1;
		}
		err = bt_le_ext_adv_start(adv, BT_LE_EXT_ADV_START_PARAM(0, 10));
		if (err) {
			LOG_ERR("failed to start advertising (err %d)\n", err);
			k_sleep(K_FOREVER);
		}
		LOG_INF("Advertising updated, %d, %d", adv_mfg_data.message.buttonstate, adv_mfg_data.message.timestamp);
		LOG_INF("Waiting for message to be sent");
		k_sem_take(&message_received, K_FOREVER);
		LOG_INF("Message was sent in advertising packets %d, %d", adv_mfg_data.message.buttonstate, adv_mfg_data.message.timestamp);

	}
}
	


int main(void)
{
	if (NRF_UICR->REGOUT0 != UICR_REGOUT0_VOUT_3V3) 
	{
		NRF_NVMC->CONFIG = NVMC_CONFIG_WEN_Wen << NVMC_CONFIG_WEN_Pos;
		while (NRF_NVMC->READY == NVMC_READY_READY_Busy){}
		NRF_UICR->REGOUT0 = UICR_REGOUT0_VOUT_3V3;

		NRF_NVMC->CONFIG = NVMC_CONFIG_WEN_Ren << NVMC_CONFIG_WEN_Pos;
		while (NRF_NVMC->READY == NVMC_READY_READY_Busy){}
	}
	int err;
	LOG_INF("Advertisement data length: %d", ARRAY_SIZE(ad));
	err = k_sem_init(&message_received, 0, 10);
	if (err) {
		LOG_ERR("Failed to initialize semaphore (err %d)\n", err);
		return -1;
	}
	k_fifo_init(&bp_fifo);


	
	k_msleep(1000);
	LOG_INF("pinmask0 0x%x, pinmask1 0x%x, array size: %d\n",  pinmask_port0, pinmask_port1,ARRAY_SIZE(buttons));
	err = bt_enable(NULL);
	if (err) {
		LOG_ERR("Bluetooth init failed (err %d)\n", err);
		return -1;
	}

	err = bt_le_ext_adv_create(adv_param, &adv_cb, &adv);
    if (err) {
        LOG_ERR("Failed to create advertiser set (%d)\n", err);
        return -1;
    }
	err = bt_le_ext_adv_set_data(adv, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
    if (err) {
        LOG_ERR("Failed to set advertising data (%d)\n", err);
        return -1;
    }

	if(!buttons_init()){
		LOG_INF("Buttons initialized");
	}
	else{
		LOG_ERR("Buttons not initialized");
	}

	while(1){
		k_sleep(K_FOREVER);
	}

	return 0;
}
