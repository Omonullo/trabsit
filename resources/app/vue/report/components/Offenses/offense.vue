<template>
  <div class="border-bottom pb-4">
    <div class="row flex-md-nowrap flex-sm-wrap">
      <div class="col-lg-3 col-md-4 col-sm-12 vehicle_id-input">
        <div
          :class="{ 'is-invalid': errors.vehicleId || errors.valid }"
          class="form-group mb-0"
        >
          <label class="d-block">{{ "Номер транспорта" | t }}</label>
          <IMaskComponent
            :class="[
            ...vehicleClasses,
            { 'is-invalid': errors.vehicleId || errors.valid },
          ]"
            :value="vehicleId || vid"
            :unmask="false"
            @accept="vehicleIdAccept"
            placeholder="00 A 000 AA"
            type="text"
            class="vehicle_id"
            :size="12"
            ref="vehicleId"
            v-autosize="vehicleId"
            v-bind="mask_opts"
          ></IMaskComponent>
          <div class="error invalid-feedback">
            {{ errors.vehicleId || errors.valid }}
          </div>
        </div>
      </div>
      <div class="col-lg-9 col-md-7 col-sm-12" v-if="offenseTypes.length">
        <div :class="{ 'is-invalid': errors.typeId }" class="form-group mb-0">
          <label>{{ "Тип нарушения" | t }}</label>
          <vSelect
            :clearable="false"
            @input="onTypeIdChange($event.value)"
            :options="
            offenseTypes.map((offenseType) => ({
              value: offenseType.id,
              label:
                offenseType[`name_${locale}`]
            }))
          "
          ></vSelect>
          <div class="error invalid-feedback">{{ errors.typeId }}</div>
        </div>
      </div>
      <button
        @click="$emit('remove', $vnode.key)"
        class="btn btn-outline-danger btn-sm btn-icon btn-circle flex-shrink-0"
        style="margin: 2.1rem 1rem 0 0"
        type="button"
        v-if="removable"
      >
        <i class="la la-remove"></i>
      </button>
    </div>
    <div class="row" v-if="selectedType && selectedType.show_details">
      <div class="col-lg-3 col-md-4 col-sm-12 vehicle_id-input"></div>
      <div class="col-lg-9 col-md-7 col-sm-12">
        <div :class="{ 'is-invalid': errors.testimony }" class="form-group mb-0">
          <label>{{ "Опишите правонарушение" | t }}</label>
          <textarea
            :class="{ 'is-invalid': errors.testimony }"
            :placeholder="
            t(
              'Любая информация, которая может помочь в точном определении правонарушения.'
            )
          "
            @input="$emit('testimonyChange', $event.target.value)"
            class="form-control"
            maxlength="500"
            required
            v-autosize="testimony"
            v-bind:value="testimony"
          ></textarea>
          <div class="error invalid-feedback">{{ errors.testimony }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { IMaskComponent } from "vue-imask";
import vSelect from "vue-select";
import {BTooltip} from "bootstrap-vue";

export default {
  components: {
    IMaskComponent,
    vSelect,
    BTooltip
  },
  mounted() {
    this.imaskComponent = this.$refs.vehicleId.maskRef;
  },
  created() {
    this.articles = window.articles;
    this.offenseTypes = window.offenseTypes;
    this.locale = window.locale;
    const imaskComponent = this.imaskComponent;
    this.mask_opts = {
      mask: [
        {
          overwrite: true,
          placeholder: "X 000000",
          mask: "X 000000",
          definitions: { X: /[Xx]/ },
          cls: "vehicle_id--green",
        },
        {
          overwrite: true,
          placeholder: "T 000000",
          mask: "T 000000",
          definitions: { T: /[Tt]/ },
          cls: "vehicle_id--green",
        },
        {
          overwrite: true,
          placeholder: "D 000000",
          mask: "D 000000",
          definitions: { D: /[Dd]/ },
          cls: "vehicle_id--green",
        },
        {
          overwrite: true,
          placeholder: "00 M 000000",
          mask: "00 M 000000",
          definitions: { M: /[Mm]/ },
          cls: "vehicle_id--green",
        },
        {
          overwrite: true,
          placeholder: "00 H 000000",
          mask: "00 H 000000",
          definitions: { H: /[Hh]/ },
          cls: "vehicle_id--yellow",
        },
        {
          overwrite: true,
          placeholder: "00 000 ##",
          mask: "00 000 ##",
          definitions: { "#": /[A-Z]/ },
          cls: "vehicle_id--flag",
        },
        {
          overwrite: true,
          placeholder: "00 000 ###",
          mask: "00 000 ###",
          definitions: { "#": /[A-Za-z]/ },
          cls: "vehicle_id--flag",
        },
        {
          overwrite: true,
          placeholder: "00 0000 ##",
          mask: "00 0000 ##",
          definitions: { "#": /[A-Za-z]/ },
          cls: "vehicle_id--flag",
        },
        {
          overwrite: true,
          placeholder: "00 # 000 ##",
          mask: "00 # 000 ##",
          definitions: { "#": /[A-Za-z]/ },
          cls: "vehicle_id--flag",
        },
        {
          overwrite: true,
          placeholder: "UN 0000",
          mask: "UN 0000",
          definitions: { U: /[Uu]/, N: /[Nn]/ },
          cls: "vehicle_id--blue",
        },
        {
          overwrite: true,
          placeholder: "00 0000 ##",
          mask: "00 0000 ##",
          definitions: { "#": /[A-Za-z]/ },
          cls: "vehicle_id--flag",
        },
        {
          overwrite: true,
          placeholder: "00 MX 0000",
          mask: "00 MX 0000",
          definitions: { M: /[Mm]/, X: /[Xx]/ },
          cls: "vehicle_id--black",
        },
        {
          overwrite: true,
          placeholder: "CMD 0000",
          mask: "CMD 0000",
          definitions: { C: /[Cc]/, M: /[Mm]/, D: /[Dd]/ },
          cls: "vehicle_id--green",
        },
        {
          overwrite: true,
          placeholder: "PAA 000",
          mask: "PAA 000",
          definitions: { P: /[Pp]/, A: /[Aa]/ },
          cls: "vehicle_id--spec",
        },
      ],
      prepare: function (str) {
        return str.toUpperCase();
      },
      dispatch: (appended, masked, flags) => {
        const inputValue = masked.rawInputValue;
        const inputs = masked.compiledMasks.map((m, index) => {
          m.reset();
          m.append(inputValue, { raw: true });
          m.append(appended, flags);
          return {
            index,
            weight: m.rawInputValue.length,
            current: m === masked.currentMask,
          };
        });

        inputs.sort((i1, i2) => {
          if (i2.weight === i1.weight) {
            if (i2.current) return 1;
            if (i1.current) return -1;
          }
          return i2.weight - i1.weight;
        });
        return masked.compiledMasks[inputs[0].index];
      },
    };
  },

  methods: {
    onTypeIdChange(typeId) {
      this.selectedTypeId = typeId
      this.$emit('typeChange', this.offenseTypes.find((offenseType) => offenseType.id === typeId))
      if (!this.selectedType.show_details) {
        this.$emit('testimonyChange', "")
      }
    },
    vehicleIdAccept(value, mask) {
      this.vehicleClasses = [
        mask.masked.isComplete ? mask.masked.currentMask.cls : "",
      ];
      this.$emit("vehicleIdChange", value.toUpperCase());
      this.$emit("validFlagChange", mask.masked.isComplete);
    },
  },

  data() {
    return {
      vid: "",
      vehicleClasses: [],
      selectedTypeId: ""
    };
  },

  props: {
    typeId: Number,
    testimony: String,
    removable: Boolean,
    valid: Boolean,
    vehicleId: String,
    errors: Object,
    id: String
  },

  computed: {
    selectedType() {
      return this.offenseTypes.find((offenseType) => offenseType.id === this.selectedTypeId)
    },
    plateInputSize() {
      if (!this.vehicleId) {
        return 10;
      } else {
        return this.vehicleId.length;
      }
    },
  },
};
</script>

<style>
input.vehicle_id {
  width: fit-content;
}

.form-control.is-invalid:not(.vehicle_id--flag) {
  background-image: none;
}

.vehicle_id-input {
  margin-bottom: 0;
}

@media (max-width: 768px) {
  .vehicle_id-input {
    margin-bottom: 1rem;
  }
}
</style>
