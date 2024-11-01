import { message } from './index';
import Axios from 'axios';

const baseURL = '/api';

let cancelToken = () => Axios.CancelToken.source();
const axios = Axios.create({
  baseURL,
  timeout: 30000,
  cancelToken: cancelToken().token,
});

function getToken(config) {
  return config;
}

axios.interceptors.request.use((config) => getToken(config), err => console.log(err));

axios.interceptors.response.use(
  (res) => {
    return res;
  },
  (error) => {
    if (error.message === 'Network Error') {
      message.error('Internet connection error', 4);
    }
    return Promise.reject(error);
  },
);

export { axios as default, cancelToken };
