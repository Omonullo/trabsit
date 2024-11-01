<template>
  <div>
    <BNav class="mb-0 border-bottom-0" tabs>
      <BNavItem :active="tabIndex===0" @click="tabIndex=0">
        <i class="fa fa-exclamation-triangle mr-0 ml-0" style="color:#fd397a" v-if="videoErrorCount!==0"></i>
        <i class="pr-2 kt-spinner kt-spinner--v2 kt-spinner--sm kt-spinner--brand" v-if="videoStatus==='uploading'"></i>
        {{"Видео"|t}} 1
        <a @click.stop="resetVideo" href="#" v-if="videoStatus!=='initial'">
          <i class="la la-close mx-0"></i>
        </a>
      </BNavItem>
      <BNavItemDropdown v-if="!withExtraVideo">
        <template slot="button-content">
          <span class="kt-label-font-color-2 mr-2">{{"Видео"|t}} 2</span>
          <i class="flaticon2-plus mx-0 kt-label-font-color-2"></i>
        </template>
        <BDropdownItem @click="updateExtraVideoType('sequel');tabIndex=1">{{"Продолжение"|t}}</BDropdownItem>
        <BDropdownItem @click="updateExtraVideoType('rear');tabIndex=1">{{"Задняя камера"|t}}</BDropdownItem>
        <BDropdownItem @click="updateExtraVideoType('remake');tabIndex=1">{{"Нарушение правил остановки и стоянки"|t}}</BDropdownItem>
      </BNavItemDropdown>
      <BNavItem :active="tabIndex===1" @click="tabIndex=1" v-else>
        <i class="fa fa-exclamation-triangle mr-0 ml-0" style="color:#fd397a" v-if="extraVideoErrorCount!==0"></i>
        <i class="px-1 kt-spinner kt-spinner--v2 kt-spinner--sm kt-spinner--brand" v-if="extraVideoStatus==='uploading'"></i>
        {{extraTabHeader}}
        <a @click.stop="handleCloseExtraTab" href="#">
          <i class="la la-close mx-0"></i>
        </a>
      </BNavItem>
    </BNav>
    <BTabs :content-class="['p-3','bg-white', 'border', 'kt-portlet', {'lt-border-0':tabIndex===0}]"
           nav-class="mb-0 border-bottom-0"
           nav-wrapper-class="d-none"
           v-model="tabIndex">
      <BTab active>
        <Dropzone :error="videoError"
                  :message="t('Внимание! На видео должна быть отображена дата съемки')"
                  @change="uploadVideo"
                  v-if="videoStatus==='initial'"></Dropzone>
        <UploadProgress :filesize="videoFilesize" :uploaded="videoUploadProgress"
                        class="mx-auto my-auto w-50" v-else-if="videoStatus==='uploading'"></UploadProgress>
        <Player :content-type="videoFileType"
                :url="videoUrl"
                :download-url="videoDownloadUrl"
                @videoChange="updateVideo" v-else-if="videoStatus==='uploaded'">
        </Player>
      </BTab>
      <BTab title-link-class="transition-none">
        <Dropzone :error="extraVideoError"
                  :message="t('Внимание! На видео должна быть отображена дата съемки')"
                  @change="uploadExtraVideo"
                  v-if="extraVideoStatus==='initial'"></Dropzone>
        <UploadProgress :filesize="extraVideoFilesize" :uploaded="extraVideoUploadProgress" class="mx-auto my-auto w-50" v-else-if="extraVideoStatus==='uploading'"></UploadProgress>
        <Player :content-type="extraVideoFileType"
                :url="extraVideoUrl"
                :download-url="extraVideoDownloadUrl"
                @videoChange="updateExtraVideo" v-else-if="extraVideoStatus==='uploaded'">
        </Player>
      </BTab>
    </BTabs>
  </div>
</template>

<script>
  import Dropzone from './dropzone.vue'
  import Player from './player.vue'
  import UploadProgress from './uploadProgress.vue'
  import {mapActions, mapGetters, mapMutations, mapState} from 'vuex'
  import {BDropdown, BDropdownItem, BNav, BNavItem, BNavItemDropdown, BTab, BTabs} from 'bootstrap-vue'

  export default {
    components: {
      Dropzone, Player, UploadProgress,
      BTabs, BTab, BDropdown, BDropdownItem,
      BNav, BNavItem, BNavItemDropdown
    },
    mounted() {

    },
    data() {
      return {
        videoInterval: null,
        videoUploadSpeed: null,
        tabIndex: 0,
      }
    },

    computed: {
      extraTabHeader() {
        if (this.extraVideoType === 'sequel') {
          return t("Продолжение")
        } else if (this.extraVideoType === 'rear') {
          return t("Задняя камера")
        } else if (this.extraVideoType === 'remake') {
          return t("Нарушение правил остановки и стоянки")
        }
      },
      ...mapState({
        videoUrl: s => s.video.url,
        videoDownloadUrl: s => s.video.downloadUrl,
        videoType: s => s.video.type,
        videoError: s => s.video.error,
        videoStatus: s => s.video.status,
        videoFileType: s => s.video.filetype,
        videoFilesize: s => s.video.filesize,
        videoErrorCount: s => s.video.errorCount,
        videoUploadProgress: s => s.video.uploadProgress,
      }),

      ...mapGetters({
        videoErrorCount: 'video/errorCount',
        extraVideoErrorCount: 'extraVideo/errorCount'
      }),

      ...mapState({
        extraVideoUrl: s => s.extraVideo.url,
        extraVideoDownloadUrl: s => s.extraVideo.downloadUrl,
        withExtraVideo: s => s.withExtraVideo,
        extraVideoType: s => s.extraVideo.type,
        extraVideoError: s => s.extraVideo.error,
        extraVideoStatus: s => s.extraVideo.status,
        extraVideoFileType: s => s.extraVideo.filetype,
        extraVideoFilesize: s => s.extraVideo.filesize,
        extraVideoUploadProgress: s => s.extraVideo.uploadProgress,
      }),
    },

    watch: {
      extraVideoError(error) {
        if (!this.videoError && error && this.tabIndex !== 1) {
          this.tabIndex=1;
        }
      },

      videoError(error) {
        if (!this.extraVideoError && error &&  this.tabIndex !== 0) {
          this.tabIndex=0;
        }
      },

      tabIndex(index) {
        if (index === 1) {
          this.updateWithExtraVideo(true)
        }
      }
    },

    methods: {
      ...mapMutations({
        updateVideo: "video/updateVideo",
        resetVideo: "video/reset",

        updateExtraVideo: "extraVideo/updateVideo",
        resetExtraVideo: "extraVideo/reset",
        updateWithExtraVideo: 'updateWithExtraVideo',
        updateExtraVideoType: "extraVideo/updateType",
      }),

      ...mapActions({
        uploadVideo: "video/uploadVideo",
        uploadExtraVideo: "extraVideo/uploadVideo",
      }),

      handleCloseExtraTab() {
        this.tabIndex = 0;
        this.updateWithExtraVideo(false);
        this.resetExtraVideo();
      },
    }
  }
</script>

<style>
  .flaticon2-plus {
    font-size: 13px !important;
  }

  .nav-link {
    padding-top: 0.5rem !important
  }

  .lt-border-0 {
    border-top-left-radius: 0;
  }

  .tab-pane.active {
    display: flex !important;
    min-height: 50vh;
  }

  @media (max-width: 768px) {
    .tab-pane.active {
      display: flex !important;
      min-height: 30vh;
    }
  }

  .dropdown-tab {
    padding: 0 0 !important;
    padding-bottom: -0.01rem !important;
  }

  .dropdown-tab .nav-link {
    padding: 0 0 !important;
  }

  .nav-link .nav-link, .nav-link .nav-link:hover {
    border-color: transparent;
  }

  .tab-pane > * {
    width: 100%
  }

  .nav-link.dropdown-toggle:after {
    display: none !important;
  }

  .la-close {
    font-size: 11pt !important;
  }

  .transition-none {
    transition: none !important;

  }
</style>
