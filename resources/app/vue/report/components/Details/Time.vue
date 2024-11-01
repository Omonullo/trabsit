<template>
  <div>
    <div class="form-group  validate" :class="{'is-invalid': errors.date}">
      <label>{{'Дата события'|t}}</label>
      <div class="row">
        <div class="col-lg-4">
          <label class="kt-option">
            <span class="kt-option__control">
              <span class="kt-radio kt-radio--check-bold">
                <input :class="{'is-invalid': errors.date}" :value="beforeYesterday" type="radio" v-model="date"/>
                <span/>
              </span>
            </span>
            <span class="kt-option__label">
              <span class="kt-option__head">
                <span class="kt-option__title">
                  {{'Позавчера'|t}}
                </span>
              </span>
              <span class="kt-option__body pt-1"> {{beforeYesterday|localDateFormat}}</span>
            </span>
          </label>
        </div>
        <div class="col-lg-4">
          <label class="kt-option">
            <span class="kt-option__control">
              <span class="kt-radio kt-radio--check-bold">
                <input :class="{'is-invalid': errors.date}" :value="yesterday" type="radio" v-model="date"/>
                <span/>
              </span>
            </span>
            <span class="kt-option__label">
              <span class="kt-option__head">
                <span class="kt-option__title">{{'Вчера'|t}}</span>
              </span>
              <span class="kt-option__body pt-1"> {{yesterday|localDateFormat}}</span>
            </span>
          </label>
        </div>
        <div class="col-lg-4">
          <label class="kt-option">
            <span class="kt-option__control">
              <span class="kt-radio kt-radio--check-bold">
                <input :class="{'is-invalid': errors.date}" :value="now" type="radio" v-model="date"/>
                <span/>
              </span>
            </span>
            <span class="kt-option__label">
              <span class="kt-option__head">
                <span class="kt-option__title">
                  {{'Сегодня'|t}}
                </span>
              </span>
              <span class="kt-option__body pt-1"> {{now|localDateFormat}}</span>
            </span>
          </label>
        </div>
      </div>
      <div class="error invalid-feedback">{{errors.date}}</div>
    </div>
    <div class="row">
      <div class="col-md-4">
        <div class="form-group">
          <label>{{'Время события'|t}}</label>
          <input class="form-control incidentTime" :class="{'is-invalid': errors.time}" ref="incidentTime" required type="text" placeholder="14:28"/>
          <div class="error invalid-feedback">{{errors.time}}</div>
        </div>
      </div>
    </div>
  </div>

</template>

<script>

  export default {
    mounted() {
      $(this.$refs.incidentTime).timepicker({
        defaultTime: this.time,
        minuteStep: 5,
        showMeridian: false
      }).on('changeTime.timepicker', e => {
        this.time = e.time.value;
      });
    },
    computed: {
      now(){
        return this.$store.state.details.now
      },
      yesterday: function () {
        return this.now.clone().subtract(1, 'days')
      },
      beforeYesterday: function () {
        return this.now.clone().subtract(2, 'days')
      },
      time: {
        get() {
          return this.$store.state.details.time;
        },
        set(time) {
          this.$store.commit('details/updateTime', time)
        }
      },
      date: {
        get() {
          return this.$store.state.details.date;
        },
        set(date) {
          this.$store.commit('details/updateDate', date)
        }
      },
      errors() {
        return this.$store.state.details.errors;
      }
    }
  }
</script>
