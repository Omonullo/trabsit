<template>
  <div>
    <transition-group class="offenses-list" name="slide">
      <offenseForm
        :key="offense.key"
        :removable="offense.removable"
        :vehicleId="offense.vehicleId"
        :typeId="offense.typeId"
        :testimony="offense.testimony"
        :valid="offense.valid"
        :errors="offense.errors"
        @vehicleIdChange="updateVehicleId({ index, vehicleId: $event })"
        @testimonyChange="updateTestimony({ index, testimony: $event })"
        @articleIdChange="updateArticleId({ index, articleId: $event })"
        @typeChange="updateType({ index, type: $event })"
        @validFlagChange="updateValidFlag({ index, valid: $event })"
        @remove="removeOffense($event)"
        :id="`${index}_item`"
        class="mb-4 offense"
        v-for="(offense, index) in offenses"
      >
      </offenseForm>
    </transition-group>
    <button
      @click="addOffense"
      class="btn btn btn-primary btn-pill"
      v-if="offenses.length < 15"
      type="button"
    >
      <span>
        <i class="la la-plus"></i>
        <span>{{ "Добавить нарушение" | t }}</span>
      </span>
    </button>
  </div>
</template>

<script>
import offenseForm from "./offense.vue";
import { mapMutations } from "vuex";

export default {
  components: { offenseForm },
  computed: {
    offenses() {
      return this.$store.state.offenses.list;
    },
  },
  methods: {
    ...mapMutations({
      removeOffense: "offenses/removeOffense",
      updateVehicleId: "offenses/updateVehicleId",
      updateArticleId: "offenses/updateArticleId",
      updateTestimony: "offenses/updateTestimony",
      updateType: "offenses/updateType",
      updateValidFlag: "offenses/updateValidFlag",
      addOffense: "offenses/addOffense",
    }),
  },
};
</script>
