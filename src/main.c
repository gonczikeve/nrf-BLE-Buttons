
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
#include <zephyr/sys/printk.h>
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



#define ADV_MIN 100/0.625
#define ADV_MAX 101/0.625
#define COMPANY_ID_CODE            0x0059//This is nordic's company ID, Algra Group should
//get their own company ID if using this solution


#define DEVICE_NAME "DynaForceButtons"
#define DEVICE_NAME_LEN (sizeof(DEVICE_NAME) - 1)
static struct bt_le_ext_adv *adv;

typedef struct __attribute__((packed)) message{
	uint8_t buttonstate;
	int32_t timestamp;
} message_t;

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



static const struct bt_data sd[] = {
	BT_DATA(BT_DATA_MANUFACTURER_DATA,&adv_mfg_data, sizeof(adv_mfg_data))
};

static struct bt_le_adv_param *adv_param =
	BT_LE_ADV_PARAM(BT_LE_ADV_OPT_NONE,
	ADV_MIN,
	ADV_MAX,
	NULL);

static struct gpio_callback button_cb_port0;
static struct gpio_callback button_cb_port1;
volatile uint32_t button_pressed = 0;
volatile uint32_t button_pressed2 = 0;
volatile uint64_t button_timestamp = 0;
volatile uint64_t button_timestamp2 = 0;

uint32_t pinmask_port0 = 0;
uint32_t pinmask_port1 = 0;

//semaphore to signal that a message has been received
static struct k_sem message_received;


void button_thread(void);
K_THREAD_DEFINE(button_thread_id, 1024, button_thread, NULL, NULL, NULL, 7, 0, 1000);


void button_pressed_cb(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	
	button_timestamp = k_uptime_get();
	button_pressed = pins;
	k_wakeup(button_thread_id);
	LOG_INF("callback2 at %d, ----------------------%d\n", button_timestamp, pins);
}

void button_pressed_cb2(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	
	button_timestamp2 = k_uptime_get();
	button_pressed2 = pins;
	k_wakeup(button_thread_id);
	LOG_INF("callback2 at %d, ----------------------%d\n", button_timestamp, pins);
}

void disable_button_interrupts(void){
	int err;
	for(int i = 0; i < ARRAY_SIZE(buttons); i++){
		err = gpio_pin_interrupt_configure_dt(&buttons[i],
					      GPIO_INT_DISABLE);
		if (err != 0) {
			LOG_INF("Error %d: failed to disable interrupt on %s pin %d",
				err, buttons[i].port->name, buttons[i].pin);
		}
	}
	
}

void enable_button_interrupts(void){
	int err;
	for(int i = 0; i < ARRAY_SIZE(buttons); i++){
		err = gpio_pin_interrupt_configure_dt(&buttons[i],
					      GPIO_INT_EDGE_TO_ACTIVE);
		if (err != 0) {
			LOG_INF("Error %d: failed to configure interrupt on %s pin %d\n",
				err, buttons[i].port->name, buttons[i].pin);
		}
	}
	
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



static uint8_t get_buttons(void)
{
	uint8_t ret = 0;
	for (size_t i = 0; i < ARRAY_SIZE(buttons); i++) {
		int val;

		val = gpio_pin_get_dt(&buttons[i]);
		if (val < 0) {
			LOG_ERR("Cannot read gpio pin");
			return 0;
		}
		if (val) {
			ret |= 1U << i;
		}
	}

	return ret;
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
	uint8_t buttons = 0;
	uint8_t prev_buttons = 0;
	int err;
	int messages_waiting = 0;
	uint64_t timestamp = 0;
	uint64_t timestamp2 = 0;
	message_t to_send[10];
	
	while(1){
		//sleep until a button is pressed
		k_sleep(K_FOREVER);

		LOG_INF("Thread woken up");
		//disable the button interrupts
		disable_button_interrupts();
		adv_mfg_data.message.buttonstate = (uint8_t)buttonstateToUint(button_pressed, button_pressed2);
		adv_mfg_data.message.timestamp = (uint32_t)button_timestamp;
		err = bt_le_ext_adv_set_data(adv, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
		if (err) {
			LOG_ERR("Failed to set advertising data (%d)\n", err);
			return -1;
		}
		LOG_INF("Message updated to %d, %d", adv_mfg_data.message.buttonstate, adv_mfg_data.message.timestamp);
		err = bt_le_ext_adv_start(adv, BT_LE_EXT_ADV_START_DEFAULT);
		if (err) {
			LOG_ERR("Advertising failed to start (err %d)\n", err);
			k_sleep(K_FOREVER);
		}
		LOG_INF("Advertising started");
		timestamp = k_uptime_get();
		//start polling buttons for subsequent presses
		do{
			LOG_INF("Polling buttons");
			k_msleep(100);
			prev_buttons = buttons;
			buttons = get_buttons();
			if(prev_buttons == 0 && buttons != 0){
				//if a button is pressed, add it to the message
				to_send[messages_waiting].buttonstate = buttons;
				timestamp = (uint32_t)k_uptime_get();
				to_send[messages_waiting].timestamp = (uint32_t)timestamp;

				messages_waiting++;
				if(messages_waiting >= 9){
					LOG_ERR("Too many messages waiting to be sent, %d", messages_waiting);
					break;
				}
				LOG_INF("Button pressed, added to message, %d", to_send[messages_waiting-1].buttonstate);
			}
			if(k_sem_take(&message_received, K_NO_WAIT) && messages_waiting) {
				//if scanner scanned our message, we can add a pending one
				messages_waiting--;
				adv_mfg_data.message = to_send[messages_waiting];
				
				err = bt_le_ext_adv_set_data(adv, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
				if (err) {
					LOG_ERR("Failed to set advertising data (%d)\n", err);
					return -1;
				}
				LOG_INF("Message sent, %d, %d", adv_mfg_data.message.buttonstate, adv_mfg_data.message.timestamp);
			}

			timestamp2 = k_uptime_get();
		}while(messages_waiting || (timestamp2 - timestamp < 3000));
		//stop advertising
		err = bt_le_ext_adv_stop(adv);
		if (err) {
			LOG_ERR("Advertising failed to stop (err %d)\n", err);
			k_sleep(K_FOREVER);
		}
		LOG_INF("Advertising stopped");
		//enable button interrupts
		enable_button_interrupts();
		LOG_INF("Thread going to sleep");

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
	k_sem_init(&message_received, 0, 10);
	
	k_msleep(1000);
	printk("pinmask0 0x%x, pinmask1 0x%x, array size: %d\n",  pinmask_port0, pinmask_port1,ARRAY_SIZE(buttons));
	err = bt_enable(NULL);
	if (err) {
		LOG_ERR("Bluetooth init failed (err %d)\n", err);
		return -1;
	}

	err = bt_le_ext_adv_create(adv_param, NULL, &adv);
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

	// err = bt_le_ext_adv_start(adv, BT_LE_EXT_ADV_START_DEFAULT);
    // if (err) {
    //     LOG_ERR("Failed to start advertising set (%d)\n", err);
    //     return -1;
    // }
	// LOG_INF("Advertising started");


	// uint32_t buttons = 0;
	// while(1){
	// 	if(button_pressed || button_pressed2){
	// 		adv_mfg_data.message.buttonstate = (uint16_t)buttonstateToUint(button_pressed, button_pressed2);
	// 		adv_mfg_data.message.timestamp = k_uptime_get();
	// 		bt_le_ext_adv_set_data(adv, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
	// 		button_pressed = 0, button_pressed2 = 0;
	// 		LOG_INF("Button pressed, advertising updated to %d", adv_mfg_data.message.buttonstate);
	// 		k_msleep(1000);
	// 	}
	// 	else{
	// 		adv_mfg_data.message.buttonstate = 0;
	// 		adv_mfg_data.message.timestamp = 0;
	// 		bt_le_ext_adv_set_data(adv, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
	// 	}
		
	// }
	return 0;
}
