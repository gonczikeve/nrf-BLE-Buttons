
#include <zephyr/kernel.h>
#include <zephyr/device.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/sys/util.h>
#include <zephyr/logging/log.h>


LOG_MODULE_REGISTER(dynaforce, LOG_LEVEL_INF);
#define BUTTONS_NODE DT_PATH(buttons)
#define GPIO0_DEV DEVICE_DT_GET(DT_NODELABEL(gpio0))
#define GPIO1_DEV DEVICE_DT_GET_OR_NULL(DT_NODELABEL(gpio1))


#define GPIO_SPEC_AND_COMMA(button) GPIO_DT_SPEC_GET(button, gpios),
static const struct gpio_dt_spec buttons[] = {
#if DT_NODE_EXISTS(BUTTONS_NODE)
	DT_FOREACH_CHILD(BUTTONS_NODE, GPIO_SPEC_AND_COMMA)
#endif
};



static struct gpio_callback button_cb_port0;
static struct gpio_callback button_cb_port1;

void button_pressed_cb(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	LOG_INF("Button pressed at %d, ----------------------%d\n", k_cycle_get_32(), pins);
}

void button_pressed_cb2(const struct device *dev, struct gpio_callback *cb, uint32_t pins)
{
	LOG_INF("callback2 at %d, ----------------------%d\n", k_cycle_get_32(), pins);
}

int buttons_init(void){
	uint32_t pinmask_port0 = 0;
	uint32_t pinmask_port1 = 0;
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
int main(void)
{
	
	LOG_INF("Hello World!, array size: %d", ARRAY_SIZE(buttons));
	if(!buttons_init()){
		LOG_INF("Buttons initialized");
	}
	else{
		LOG_ERR("Buttons not initialized");
	}



	uint32_t buttons = 0;
	while(1){
		buttons = get_buttons();
		LOG_INF("Buttons: %u", buttons);
		LOG_INF("sleeping");
		k_msleep(5000);
	}
	return 0;
}
