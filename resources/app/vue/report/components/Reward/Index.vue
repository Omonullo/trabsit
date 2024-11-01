<template>
  <div>
    <div class="form-group">
      <label>{{ "Вид вознаграждения" | t }}</label>
      <div class="row">
        <div
          class="col-lg-6 mb-2"
          v-for="(reward, name) in types"
          v-if="!reward.hidden"
        >
          <label
            :class="{ 'position-relative overflow-hidden': reward.unavailable }"
            class="kt-option"
            style="min-height: 9.25rem"
          >
            <div class="ribbon" v-if="reward.unavailable">
              {{ "Скоро" | t }}
            </div>
            <span class="kt-option__control">
              <span class="kt-radio kt-radio--check-bold">
                <input
                  :disabled="reward.unavailable"
                  :value="name"
                  required
                  type="radio"
                  v-model="type"
                />
                <span></span>
              </span>
            </span>
            <span class="kt-option__label">
              <span class="kt-option__head">
                <span class="kt-option__title">{{ reward.name | t }}</span>
              </span>
              <span class="kt-option__body d-flex justify-content-start">
                <span class="mr-2 reward-icon">{{ reward.icon }}</span>
                <span>{{ reward.description | t }}</span>
              </span>
            </span>
          </label>
        </div>
      </div>
    </div>

    <div class="row" v-show="type === 'phone'">
      <div class="col-md-6">
        <div class="form-group">
          <label>{{ "Номер мобильного телефона для пополнения" | t }}</label>
          <IMaskComponent
            :class="[{ 'is-invalid': errors.phone || errors.phoneValid }]"
            :value="phone"
            @accept="onAcceptPhone"
            class="form-control"
            type="text"
            v-bind="{ mask: '+\\9\\9\\8 00 000 00 00' }"
          >
          </IMaskComponent>
          <div class="error invalid-feedback">
            {{ errors.phone || errors.phoneValid }}
          </div>
        </div>
      </div>
    </div>
    <div class="row" v-show="type === 'fund'">
      <div class="col-md-6">
        <div class="form-group">
          <label>{{ "Благотворительный фонд" | t }}</label>
          <select
            :class="[{ 'is-invalid': errors.fund }]"
            class="form-control"
            v-model="fund"
          >
            <option></option>
            <option :value="fundName" v-for="(_, fundName) in funds">
              {{ fundName | t }}
            </option>
          </select>
          <div class="error invalid-feedback">{{ errors.fund }}</div>
          <div class="mt-2">
            <span>{{ "Информация о благотворительных фондах" | t }}:</span>
            <ul>
              <li v-for="(fundUrl, fundName) in funds">
                <a
                  target="_blank"
                  :href="fundUrl"
                  :class="{ 'selected-fund-item': fund === fundName }"
                  >{{ fundName | t }}</a
                >
              </li>
            </ul>
          </div>
        </div>
      </div>
    </div>
    <div class="row" v-show="type === 'bank'">
      <div class="col-md-6">
        <div class="form-group">
          <label>{{ "Юрлицо" | t }}</label>
          <select
            :class="[{ 'is-invalid': errors.bank }]"
            class="form-control"
            v-model="bank"
          >
            <option
              :value="organization.bank_account"
              v-for="organization in organizations"
            >
              {{ organization.name }} - {{ organization.bank_account }}
            </option>
          </select>
          <div class="error invalid-feedback">{{ errors.bank }}</div>
        </div>
      </div>
    </div>
    <div class="row" style="display: none" v-show="type === 'card'">
      <div class="col-md-12">
        <div class="form-group">
          <p>{{ "Вознаграждения будут перечислены на банковскую карту, указанную в профиле. Процесс перевода средств осуществляется ежедневно на основе совокупности всех зачислений, подлежащих выплате на данный момент. Следовательно, все начисленные вознаграждения за период будут объединены в один платеж." | t }}</p>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import { IMaskComponent } from "vue-imask";
export default {
  mounted() {
    this.funds = window.funds;
    this.types = window.rewardTypes;
    this.organizations = window.organizations;
    this.phone = window.profile.phone;
  },

  components: { IMaskComponent },
  methods: {
    onAcceptPhone(e, mask) {
      this.phone = e;
      this.phoneValid = mask.masked.isComplete;
    },

    async onAcceptCard(number, mask) {
      let card = {};
      if (mask.masked.isComplete) {
        card = await this.$store.dispatch("reward/sendCardNumber", number);
        card.error = card.error || "";
      } else {
        card.error = t("Номер карты не верен");
      }
      this.card = {
        ...card,
        number,
      };
    },
  },

  computed: {
    bank: {
      get() {
        return this.$store.state.reward.bank;
      },
      set(bank) {
        this.$store.commit("reward/updateBank", bank);
      },
    },

    fund: {
      get() {
        return this.$store.state.reward.fund;
      },
      set(fund) {
        this.$store.commit("reward/updateFund", fund);
      },
    },

    type: {
      get() {
        return this.$store.state.reward.type;
      },
      set(type) {
        this.$store.commit("reward/updateType", type);
      },
    },

    phone: {
      get() {
        return this.$store.state.reward.phone;
      },
      set(phone) {
        this.$store.commit("reward/updatePhone", phone);
      },
    },

    phoneValid: {
      get() {
        return this.$store.state.reward.phoneValid;
      },
      set(valid) {
        this.$store.commit("reward/updatePhoneValidFlag", valid);
      },
    },

    errors() {
      return this.$store.state.reward.errors;
    },
  },
};
</script>
