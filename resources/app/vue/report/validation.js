import BaseJoi from "joi-browser";
import Extension from "@hapi/joi-date";

const Joi = BaseJoi.extend(Extension);

function hasErrors(state) {
  if (state.errors) return Object.keys(state.errors).length !== 0;
}

function message(str) {
  return { message: str };
}

function clearError(state, prop) {
  state.errors[prop] = "";
}

function errorCount(state) {
  return Object.keys(state.errors).reduce(
    (acc, k) => (!state.errors[k] ? acc : acc + 1),
    0
  );
}

const detailsScheme = Joi.object().keys({
  address: Joi.string()
    .required()
    .error((_) => message(t("Укажите адрес"))),
  time: Joi.date()
    .format("H:mm")
    .required()
    .error((_) => message(t("Укажите время"))),
  date: Joi.date()
    .format("DD.MM.YYYY")
    .max("now")
    .required()
    .error((_) => message(t("Укажите дату"))),
  district: Joi.object()
    .required()
    .keys({
      id: Joi.string().required(),
    })
    .error((_) => message(t("Выберите район"))),
  area: Joi.object()
    .required()
    .keys({
      id: Joi.string().required(),
    })
    .error((_) => message(t("Выберите область"))),
  coords: Joi.array()
    .required()
    .items(Joi.number().required().min(0), Joi.number().required().min(0))
    .error((_) => message(t("Введите правильные координаты"))),
});

const offenseScheme = Joi.object().keys({
  vehicleId: Joi.string()
    .required()
    .error((_) => message(t("Укажите номер машины"))),
  valid: Joi.boolean()
    .invalid(false)
    .required()
    .error((_) => message(t("Номер машины не верен"))),
  testimony: Joi.when("type", {
    is: Joi.object().keys({
      show_details: Joi.boolean().valid(true),
    }),
    then: Joi.string()
      .required()
      .error((_) => message(t("Опишите нарушение"))),
  }),
  typeId: Joi.number()
    .required()
    .error((_) => message(t("Выберите тип нарушения"))),
  citizenArticleId: Joi.number()
    .optional()
    .error((_) => message(t("Выберите статью"))),
});

const rewardScheme = Joi.object().keys({
  type: Joi.string()
    .required()
    .valid(...Object.keys(window.rewardTypes)),
  phone: Joi.when("type", {
    is: Joi.string().valid("phone"),
    then: Joi.string().required(),
  }).error((_) => message(t("Укажите номер телефона"))),

  bank: Joi.when("type", {
    is: Joi.string().valid("bank"),
    then: Joi.string().required(),
  }).error((_) => message(t("Выберите юрлицо"))),

  phoneValid: Joi.boolean()
    .when("type", {
      is: Joi.string().valid("phone"),
      then: Joi.invalid(false).required(),
    })
    .error((_) => message(t("Номер телефона не верен"))),

  fund: Joi.when("type", {
    is: Joi.string().valid("fund"),
    then: Joi.string().required(),
  }).error((_) => message(t("Выберите фонд"))),
});

const offenseListScheme = Joi.array().items(offenseScheme);

function validator(scheme) {
  return {
    async validateList({ state, commit }) {
      let errors = {};
      try {
        await Joi.validate(state.list, scheme, {
          abortEarly: false,
          allowUnknown: true,
        });
      } catch (err) {
        if (err) {
          for (let i = 0; i < err.details.length; i++) {
            let [index, key] = err.details[i].path;
            if (!errors[index]) errors[index] = {};
            errors[index][key] = err.details[i].message;
          }
        }
      }
      commit("updateErrors", errors);
      return Object.keys(errors).length === 0;
    },

    async validate({ state, commit }) {
      let errors = {};
      try {
        await Joi.validate(state, scheme, {
          abortEarly: false,
          allowUnknown: true,
        });
      } catch (err) {
        if (err) {
          errors = err.details.reduce((acc, e) => {
            return { ...acc, [e.path[0]]: e.message };
          }, {});
        }
      }
      commit("updateErrors", errors);
      return Object.keys(errors).length === 0;
    },
  };
}

export {
  validator,
  clearError,
  hasErrors,
  errorCount,
  rewardScheme,
  detailsScheme,
  offenseListScheme,
};
