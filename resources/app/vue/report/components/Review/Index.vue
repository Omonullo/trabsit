<template>
  <div>
    <div class="kt-wizard-v3__review">
      <div class="kt-wizard-v3__review-item">
        <div class="kt-wizard-v3__review-title">
          {{ "Видео файл" | t }}
        </div>
        <div class="kt-wizard-v3__review-content">
          <VideoInfo v-bind="video"></VideoInfo>
        </div>
      </div>
      <div class="kt-wizard-v3__review-item" v-if="withExtraVideo">
        <div class="kt-wizard-v3__review-title">
          {{ "Второй видео файл" | t }}
        </div>
        <div class="kt-wizard-v3__review-content">
          <VideoInfo v-bind="extraVideo"></VideoInfo>
        </div>
      </div>

      <div class="kt-wizard-v3__review-item">
        <div class="kt-wizard-v3__review-title">
          {{ "Детали видеозаписи" | t }}
        </div>
        <div class="kt-wizard-v3__review-content">
          {{ "Место события" | t }}:
          <Info
            :text="details && details.address"
            :warn="
              details.errors.address ||
              details.errors.area ||
              details.errors.district
            "
          ></Info>
          <br />
          {{ "Координаты" | t }}:
          <Info
            :text="details && `${details.coords[1]}, ${details.coords[0]}`"
            :warn="details.errors.coords"
          ></Info>
          <br />
          {{ "Время" | t }}:
          <Info
            :text="details && details.time"
            :warn="details.errors.time"
          ></Info>
          <br />
        </div>
      </div>
      <div class="kt-wizard-v3__review-item">
        <div class="kt-wizard-v3__review-title">
          {{ "Нарушения" | t }}
        </div>
        <div class="kt-wizard-v3__review-content">
          <ul id="review-offenses">
            <li :key="offense.key" v-for="offense in offenses">
              <Info
                :text="offense.vehicleId"
                :warn="offense.errors.vehicleId || offense.errors.valid"
              ></Info>{{ !offense.typeId ? "," : "" }}
              <Info
                :text="offenseTypes[offense.typeId]? offenseTypes[offense.typeId][`name_${locale||'ru'}`]: null"
                :warn="offense.errors.typeId"
              ></Info>{{ !offense.testimony ? "," : "" }}
              <Info
                v-if="offense.testimony"
                :text="offense.testimony"
                :warn="offense.errors.testimony"
              ></Info>{{ offense.citizenArticleId ? "," : "" }}
              <Info
                v-if="offense.citizenArticleId"
                :text="
                  articles[offense.citizenArticleId] &&
                  articles[offense.citizenArticleId][`alias_${locale}`]
                "
              ></Info>
            </li>
          </ul>
        </div>
      </div>
      <div class="kt-wizard-v3__review-item">
        <div class="kt-wizard-v3__review-title">
          {{ "Вознаграждение" | t }}
        </div>
        <div
          class="kt-wizard-v3__review-content"
          v-if="reward.type === 'phone'"
        >
          {{ "Пополнение мобильного счета" | t }}:
          <Info
            :text="reward.phone"
            :warn="reward.errors.phone || reward.errors.phoneValid"
          ></Info>
          <br />
        </div>
        <div
          class="kt-wizard-v3__review-content"
          v-else-if="reward.type === 'card'"
        >
          {{ "Перевод на карту" | t }}:
          <Info :text="'На карту указанную в профиле' | t" :warn="reward.errors.card"></Info>
          <br />
        </div>
        <div
          class="kt-wizard-v3__review-content"
          v-else-if="reward.type === 'fund'"
        >
          {{ "Пожертвование в фонд" | t }}:
          <Info :text="reward.fund" :warn="reward.errors.fund"></Info>
          <br />
        </div>
        <div
          class="kt-wizard-v3__review-content"
          v-else-if="reward.type === 'bank'"
        >
          {{ "Банковский перевод" | t }}:
          <Info :text="reward.bank" :warn="reward.errors.bank"></Info>
          <br />
        </div>
        <div
          class="kt-wizard-v3__review-content"
          v-else-if="reward.type === 'no-reward'"
        >
          {{ "Без вознаграждения" | t }}
          <br />
        </div>
        <div class="kt-wizard-v3__review-content" v-else>
          <Info></Info>
          <br />
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import VideoInfo from "./videoInfo.vue";
import Info from "./info.vue";
import * as R from "ramda";

export default {
  components: { VideoInfo, Info },
  created() {
    this.articles = R.indexBy(R.prop("id"), window.articles);
    this.offenseTypes = R.indexBy(R.prop("id"), window.offenseTypes);
    this.locale = window.locale;
  },
  computed: {
    withExtraVideo: function () {
      return this.$store.state.withExtraVideo;
    },
    video() {
      return {
        ...this.$store.state.video,
        size: this.$store.getters["video/size"],
        name: this.$store.getters["video/name"],
      };
    },
    extraVideo() {
      return {
        ...this.$store.state.extraVideo,
        size: this.$store.getters["extraVideo/size"],
        name: this.$store.getters["extraVideo/name"],
      };
    },
    details() {
      return {
        ...this.$store.state.details,
        address: this.$store.getters["details/yAddress"],
        errorCount: this.$store.getters["details/errorCount"],
      };
    },
    offenses() {
      return this.$store.state.offenses.list;
    },

    reward() {
      return this.$store.state.reward;
    },
  },
};
</script>
