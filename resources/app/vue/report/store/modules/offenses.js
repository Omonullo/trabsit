import {
  clearError,
  errorCount,
  offenseListScheme,
  validator,
} from "../../validation";

function uid() {
  return ([1e7] + -1e3 + -4e3 + -8e3 + -1e11).replace(/[018]/g, (c) =>
    (
      c ^
      (crypto.getRandomValues(new Uint8Array(1))[0] & (15 >> (c / 4)))
    ).toString(16)
  );
}

const state = {
  list: [
    {
      vehicleId: undefined,
      citizenArticleId: undefined,
      valid: false,
      removable: false,
      key: uid(),
      errors: {
        vehicleId: "",
        citizenArticleId: "",
        typeId: "",
      },
    },
  ],
};

const getters = {
  errorCount(state) {
    let count = 0;
    state.list.forEach(function (offense) {
      let errors = { ...offense.errors };
      if (!errors.vehicleId) errors.vehicleId = offense.errors.valid;
      errors.valid = "";
      count += errorCount({ errors });
    });
    return count;
  },
};

const actions = {
  ...validator(offenseListScheme),
};

const mutations = {
  addOffense(state) {
    state.list = [
      ...state.list,
      {
        vehicleId: undefined,
        removable: true,
        key: uid(),
        errors: {
          vehicleId: "",
          typeId: "",
        },
      },
    ];
  },

  removeOffense(state, key) {
    state.list = state.list.filter((o) => o.key !== key);
  },

  updateVehicleId(state, { index, vehicleId }) {
    state.list[index].vehicleId = vehicleId;
    clearError(state.list[index], "vehicleId");
  },

  updateValidFlag(state, { index, valid }) {
    state.list[index].valid = valid;
    clearError(state.list[index], "valid");
  },

  updateTestimony(state, { index, testimony }) {
    state.list[index].testimony = testimony;
    clearError(state.list[index], "testimony");
  },

  updateArticleId(state, { index, articleId }) {
    state.list[index].citizenArticleId = articleId;
    clearError(state.list[index], "citizenArticleId");
  },

  updateType(state, { index, type }) {
    const offense = state.list[index];
    offense.type = type;
    offense.typeId = type.id;
    clearError(offense, "typeId");
    clearError(offense, "type");
  },

  updateErrors(state, errors) {
    state.list = state.list.map((o, i) => ({
      ...o,
      errors: errors[i] || { vehicleId: "", typeId: "" },
    }));
  },
};

export default {
  namespaced: true,
  state,
  getters,
  actions,
  mutations,
};
