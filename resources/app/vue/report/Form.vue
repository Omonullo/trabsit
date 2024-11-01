<template>
  <div>
    <VideoUploadForm></VideoUploadForm>
    <div class="kt-portlet mt-4">
      <div class="kt-portlet__body kt-portlet__body--fit">
        <div class="kt-grid kt-wizard-v3 kt-wizard-v3--white"
             data-ktwizard-state="first"
             id="wizard">
          <div class="kt-grid__item">
            <div class="kt-wizard-v3__nav">
              <div class="kt-wizard-v3__nav-items row justify-content-center">
                <a class="kt-wizard-v3__nav-item col-sm-12 col-lg-3"
                   data-ktwizard-state="current"
                   data-ktwizard-type="step"
                   href="#d">
                  <div class="kt-wizard-v3__nav-body">
                    <div class="kt-wizard-v3__nav-label">
                      <span class="step-number">1</span>
                      <span>{{"Нарушения"|t}}</span>
                      <i class="fa fa-exclamation-triangle btn-font-danger ml-2" v-if="offensesErrorCount!==0"></i>
                    </div>
                    <div class="kt-wizard-v3__nav-bar"></div>
                  </div>
                </a>
                <a class="kt-wizard-v3__nav-item col-sm-12 col-lg-3"
                   data-ktwizard-state="pending"
                   data-ktwizard-type="step">
                  <div class="kt-wizard-v3__nav-body">
                    <div class="kt-wizard-v3__nav-label">
                      <span class="step-number">2</span>
                      <span>
                        {{"Место и время"|t}}
                      </span>
                      <i class="fa fa-exclamation-triangle btn-font-danger ml-2" v-if="detailsErrorCount!==0"></i>
                    </div>
                    <div class="kt-wizard-v3__nav-bar"></div>
                  </div>
                </a>
                <a class="kt-wizard-v3__nav-item col-sm-12 col-lg-3"
                   data-ktwizard-state="pending"
                   data-ktwizard-type="step"
                   href="#d">
                  <div class="kt-wizard-v3__nav-body">
                    <div class="kt-wizard-v3__nav-label">
                      <span class="step-number">3</span>
                      <span>{{"Вознаграждение"|t}}</span>
                      <i class="fa fa-exclamation-triangle btn-font-danger ml-2" v-if="rewardErrorCount!==0"></i>
                    </div>
                    <div class="kt-wizard-v3__nav-bar"></div>
                  </div>
                </a>
                <a class="kt-wizard-v3__nav-item col-sm-12 col-lg-3"
                   data-ktwizard-state="pending"
                   data-ktwizard-type="step"
                   href="#d">
                  <div class="kt-wizard-v3__nav-body">
                    <div class="kt-wizard-v3__nav-label">
                      <span class="step-number">4</span>
                      <span>{{"Просмотр"|t}}</span>
                    </div>
                    <div class="kt-wizard-v3__nav-bar"></div>
                  </div>
                </a>
              </div>
            </div>
          </div>
          <div class="kt-grid__item kt-grid__item--fluid kt-wizard-v3__wrapper pt-0">
            <form class="kt-form pt-3" id="kt_form" novalidate="novalidate">

              <div class="kt-wizard-v3__content" data-ktwizard-state="current" data-ktwizard-type="step-content">
                <div class="kt-heading kt-heading--md">
                  {{"Укажите зафиксированные нарушения"|t}}
                </div>
                <div class="kt-form__section kt-form__section--first">
                  <div class="kt-wizard-v3__form">
                    <OffensesForm></OffensesForm>
                  </div>
                </div>
              </div>
              <div class="kt-wizard-v3__content"
                   data-ktwizard-type="step-content">
                <div class="kt-heading kt-heading--md">
                  {{('Укажите место и время события')}}
                </div>
                <div class="kt-form__section kt-form__section--first">
                  <div class="kt-wizard-v3__form">
                    <div class="kt-wizard-v1__form">
                      <DetailsForm></DetailsForm>
                    </div>
                  </div>
                </div>
              </div>
              <div class="kt-wizard-v3__content" data-ktwizard-type="step-content">
                <div class="kt-heading kt-heading--md"> {{"Укажите данные для вознаграждения"|t}}</div>
                <RewardForm></RewardForm>
              </div>
              <div class="kt-wizard-v3__content" data-ktwizard-type="step-content">
                <div class="kt-heading kt-heading--md">
                  {{"Проверьте заполненные данные и отправьте"|t}}
                </div>
                <div class="kt-form__section kt-form__section--first">
                  <Review></Review>
                </div>
              </div>
              <div class="kt-form__actions">
                <div class="btn btn-secondary btn-md btn-tall btn-wide kt-font-bold
                          kt-font-transform-u"
                     data-ktwizard-type="action-prev">
                  {{"Назад"|t}}
                </div>
                <button @click="$store.dispatch('sendReport')"
                        class="btn btn-success btn-md btn-tall btn-wide kt-font-bold
                          kt-font-transform-u" data-ktwizard-type="action-submit" type="button">
                  {{"Отправить"|t}}
                </button>
                <div class="btn btn-brand btn-md btn-tall btn-wide kt-font-bold
                          kt-font-transform-u"
                     data-ktwizard-type="action-next">
                  {{"Вперед"|t}}
                </div>
              </div>
            </form>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
  import {mapGetters} from 'vuex'
  import DetailsForm from './components/Details/Index.vue'
  import VideoUploadForm from "./components/VideoUpload/Index.vue";
  import OffensesForm from "./components/Offenses/Index.vue";
  import RewardForm from "./components/Reward/Index.vue";
  import Review from "./components/Review/Index.vue";

  const steps = ['offenses', 'details', 'reward', 'review']
  export default {
    components: {VideoUploadForm, OffensesForm, RewardForm, Review, DetailsForm},
    mounted() {
      new KTWizard("wizard", {startStep: 1}).on('change',
        (e) => {
          if (this.currentTab !== 'offenses') {
            this.$store.dispatch(this.currentTab + '/validate');
          } else if (this.currentTab !== 'review') {
            this.$store.dispatch(this.currentTab + '/validateList');
          }
          this.currentTab = steps[e.getStep() - 1]
        });
    },
    computed: {
      ...mapGetters({
        errorCount: 'errorCount',
        detailsErrorCount: 'details/errorCount',
        offensesErrorCount: 'offenses/errorCount',
        rewardErrorCount: 'reward/errorCount'
      }),
    },

    data() {
      return {
        hasVideoInputted: false,
        currentTab: 'offenses',
        date: moment().format("DD.MM.YYYY")
      }
    },

    methods: {}
  }
</script>


<style>
  .is-valid .valid-feedback {
    display: inline-block
  }

  .is-invalid .invalid-feedback {
    display: inline-block
  }

  .kt-badge {
    font-size: 0.8rem !important;
    padding: 0.65rem 0.45rem
  }

  .nav-link {
    padding-top: 0.6rem !important;
    padding-bottom: 0.5rem !important;
  }

  @-webkit-keyframes fadeInUp {
    from {
      opacity: 0;
      -webkit-transform: translate3d(0, 100%, 0);
      transform: translate3d(0, 100%, 0)
    }

    to {
      opacity: 1;
      -webkit-transform: translate3d(0, 0, 0);
      transform: translate3d(0, 0, 0)
    }
  }

  @keyframes fadeInUp {
    from {
      opacity: 0;
      -webkit-transform: translate3d(0, 100%, 0);
      transform: translate3d(0, 100%, 0)
    }

    to {
      opacity: 1;
      -webkit-transform: translate3d(0, 0, 0);
      transform: translate3d(0, 0, 0)
    }
  }

  .fadeInUp {
    -webkit-animation-name: fadeInUp;
    animation-name: fadeInUp
  }

  @-webkit-keyframes fadeOut {
    from {
      opacity: 1;
    }

    to {
      opacity: 0;
    }
  }

  @keyframes fadeOut {
    from {
      opacity: 1;
    }

    to {
      opacity: 0;
    }
  }

  .fadeOut {
    -webkit-animation-name: fadeOut;
    animation-name: fadeOut;
  }


  .animated {
    -webkit-animation-duration: 1s;
    animation-duration: 1s;
    -webkit-animation-fill-mode: both;
    animation-fill-mode: both;
  }

  .offenses-list {
    overflow-y: hidden;
  }

  .slide-leave-active,
  .slide-enter-to {
    height: 75px;
    opacity: 1;
    transition: all 0.5s;
  }

  .slide-enter {
    transition: all 0.5s;
    opacity: 0;
    height: 0;
    margin-bottom: 0 !important;
  }

  .slide-leave-to {
    opacity: 0;
    height: 0;
    margin-bottom: 0 !important;
  }
</style>
