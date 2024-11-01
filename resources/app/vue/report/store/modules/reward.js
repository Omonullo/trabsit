import {
  clearError,
  errorCount,
  rewardScheme,
  validator,
} from "../../validation";
import axios from "axios";

const actions = {
  ...validator(rewardScheme),
  async sendCardNumber(_, card) {
    let result;
    let data = new FormData();
    data.set("card", card);
    try {
      result = (
        await axios.post("/verify-card", data, {
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
            "x-csrf-token": window.csrf,
          },
        })
      ).data;
    } catch (e) {
      result = e.response;
    }
    return result;
  },
};

const state = {
  card: {
    error: "Не указано",
    number: "",
    owner: "",
    bank: "",
  },
  fund: "",
  phone: "",
  bank: "",
  type: "phone",
  phoneValid: false,
  errors: {
    type: "",
    phone: "",
    valid: "",
    card: "",
  },
};

const mutations = {
  updateBank(state, bank) {
    state.bank = bank;
    clearError(state, "bank");
  },

  updatePhone(state, phone) {
    state.phone = phone;
    clearError(state, "phone");
  },

  updateType(state, type) {
    state.type = type;
    state.valid = false;
    clearError(state, "type");
  },

  updateFund(state, fund) {
    state.fund = fund;
    clearError(state, "fund");
  },

  updatePhoneValidFlag(state, save) {
    state.phoneValid = save;
  },

  updateErrors(state, errors) {
    state.errors = errors;
  },
};

const getters = {
  errorCount(state) {
    let errors = { ...state.errors };
    if (!errors.phone) errors.phone = errors.valid;
    errors.valid = "";
    return errorCount({ errors });
  },
};
export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations,
};
