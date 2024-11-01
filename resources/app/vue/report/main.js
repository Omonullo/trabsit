import Vue from "vue";
import App from "./Form.vue";
import mixins from './mixins.js'
import store from './store'
import VueAutosize from 'autosize-vue'

window.csrf =$('[name=__anti-forgery-token]').val();

Vue.config.productionTip = false;
Vue.mixin(mixins);
Vue.use(VueAutosize);

let vm = new Vue({
  store,
  render: h => h(App)
}).$mount("#report-app");

