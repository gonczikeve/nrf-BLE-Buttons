
#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/sys/util.h>
#include <zephyr/logging/log.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/gap.h>

LOG_MODULE_REGISTER(dynaforce, LOG_LEVEL_INF);
#define BUTTONS_NODE DT_PATH(buttons)
#define GPIO0_DEV DEVICE_DT_GET(DT_NODELABEL(gpio0))
#define GPIO1_DEV DEVICE_DT_GET_OR_NULL(DT_NODELABEL(gpio1))

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

typedef struct adv_mfg_data {
	uint16_t company_code;	    /* Company Identifier Code. */
	uint16_t buttonstate;      
} adv_mfg_data_type;

static adv_mfg_data_type adv_mfg_data = {COMPANY_ID_CODE,0x00};

static const struct bt_data ad[] = {
	BT_DATA_BYTES(BT_DATA_FLAGS, BT_LE_AD_NO_BREDR),
	BT_DATA(BT_DATA_NAME_COMPLETE, DEVICE_NAME, DEVICE_NAME_LEN),
	BT_DATA(BT_DATA_MANUFACTURER_DATA,&adv_mfg_data, sizeof(adv_mfg_data))
};

static unsigned char url_data[] = { 0x17, '/', '/', 'a', 'c', 'a', 'd', 'e', 'm',
				    'y',  '.', 'n', 'o', 'r', 'd', 'i', 'c', 's',
				    'e',  'm', 'i', '.', 'c', 'o', 'm' };

static const struct bt_data sd[] = {
	BT_DATA(BT_DATA_URI, url_data, sizeof(url_data)),
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
uint32_t pinmask_port0 = 0;
uint32_t pinmask_port1 = 0;


void button_pressed_cb(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	
	LOG_INF("Button pressed at %d, ----------------------%d\n", k_cycle_get_32(), pins);
	button_pressed = pins;
}

void button_pressed_cb2(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	LOG_INF("callback2 at %d, ----------------------%d\n", k_cycle_get_32(), pins);
	button_pressed2 = pins;
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
		printk("Error: button device %s is not ready\n",
		       buttons[i].port->name);
		return 0;
		}
		else{
			printk("Button device %s is ready\n",
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
			LOG_INF("Button %s pin %d configured\n",
				buttons[i].port->name, buttons[i].pin);
		}
	}

	for(int i = 0; i < ARRAY_SIZE(buttons); i++){
		ret = gpio_pin_interrupt_configure_dt(&buttons[i],
					      GPIO_INT_EDGE_TO_ACTIVE);
		if (ret != 0) {
			printk("Error %d: failed to configure interrupt on %s pin %d\n",
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
		printk("Set up button at %s pin %d\n", buttons[i].port->name, buttons[i].pin);
	}



	return 0;
}
static uint32_t get_buttons(void)
{
	uint32_t ret = 0;
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
	for(int i = 0; i < 32; i++){
		if(pinmask_port0 & (1 << i)){
			ret |= (buttonstate>>i)<<ctr;
			ctr++;
			if(ctr > 3){
				LOG_ERR("Too many buttons pressed");
				break;
			}
		}
	}
	ctr = 0;
	for(int i = 0; i < 32; i++){
		if(pinmask_port1 & (1 << i)){
			ret |= (buttonstate2>>i)<<(ctr + 4);
			ctr++;
			if(ctr > 3){
				LOG_ERR("Too many buttons pressed");
				break;
			}
		}
	}

	return ret;
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
	LOG_INF("Hello World!, array size: %d", ARRAY_SIZE(buttons));
	if(!buttons_init()){
		LOG_INF("Buttons initialized");
	}
	else{
		LOG_ERR("Buttons not initialized");
	}

	err = bt_enable(NULL);
	if (err) {
		LOG_ERR("Bluetooth init failed (err %d)\n", err);
		return;
	}

	err = bt_le_adv_start(adv_param, ad, ARRAY_SIZE(ad), sd, ARRAY_SIZE(sd));
	if (err) {
		LOG_ERR("Advertising failed to start (err %d)\n", err);
		return;
	}

	uint32_t buttons = 0;
	while(1){
		if(button_pressed || button_pressed2){
			adv_mfg_data.buttonstate = (uint16_t)buttonstateToUint(button_pressed, button_pressed2);
			bt_le_adv_update_data(ad, ARRAY_SIZE(ad), NULL, 0);
			button_pressed = 0, button_pressed2 = 0;
			LOG_INF("Button pressed, advertising updated to %d", adv_mfg_data.buttonstate);
			k_msleep(1000);
		}
		else{
			adv_mfg_data.buttonstate = 0;
			bt_le_adv_update_data(ad, ARRAY_SIZE(ad), NULL, 0);
		}
		
	}
	return 0;
}
