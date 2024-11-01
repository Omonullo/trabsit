import {clearError, detailsScheme, errorCount, validator} from '../../validation'
const actions = {
  ...validator(detailsScheme)
};

const state = {
  now: moment(),
  time: '',
  date: '',
  area: {},
  address: null,
  district: {},
  coords: [41.2995, 69.2401],
  errors: {
    time: '',
    date: '',
    area: '',
    address: '',
    district: '',
    coords: '',
  }
};


const getters = {
  errorCount,
  yAddress(state) {
    let str = '';
    if (state.area && state.area.name)
      str += state.area.name;
    if (state.district && state.district.name)
      str += ', ' + state.district.name
    if (state.address)
      str += ', ' + state.address
    return str;
  },
};


const mutations = {
  updateDetails(state, details) {
    state = {...state, ...details}
  },

  refreshNow(){
    state.now = moment()
  },

  updateErrors(state, errors) {
    state.errors = errors
  },

  updateDate(state, date) {
    state.date = date;
    clearError(state, 'date');
  },

  updateTime(state, time) {
    state.time = time;
    clearError(state, 'time');
  },

  updateAddress(state, address) {
    state.address = address;
    clearError(state, 'address');
  },

  updateArea(state, area) {
    state.district = null;
    state.area = area;
    clearError(state, 'area');
  },

  updateDistrict(state, district) {
    state.district = district;
    if (district)
      clearError(state, 'district');

  },

  updateCoords(state, coords) {
    state.coords = coords;
    clearError(state, 'coords');
  },

};


export default {
  namespaced: true,
  state,
  actions,
  getters,
  mutations
}
