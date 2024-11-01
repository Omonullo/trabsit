<template>
  <div :style="{minHeight:'50vh'}" class="player" ref="player" tabindex="1">
    <video :class="{fullscreen:isFullScreen}"
           @ended="paused=true"
           @loadedmetadata="handleLoadMetaData"
           @timeupdate="handleTimeUpdate"
           crossorigin="anonymous"
           ref="video">
      <source :src="url"
              :type="contentType">
    </video>
    <div class="player__toolbar">
      <button @click="paused=!paused" class="player__play">
        <img alt="play" src="/img/play.svg" style="width:68%" v-if="paused">
        <img alt="pause" src="/img/pause.svg" style="width:68%" v-else>
      </button>
      <div class="player__track">
        <div @click="seek"
             @mousedown="addMoveListener"
             @mouseleave="removeListener"
             @mouseup="removeListener"
             class="progress bg-dark"
             ref="progress">
          <div :style="{width:playBackBarWidth+'%'}"
               class="progress-bar bg-white player__progress"></div>
        </div>
      </div>
      <div class="player__duration">{{format_seconds(currentTime)}}</div>
      <a :href="downloadUrl" class="player__download btn" download><img alt="download" src="/img/download.svg"></a>
      <button @click="togglePlayerFullscreen"
              class="player__fullscreen">
        <img alt="fullscreen" src="/img/fullscreen_off.svg" style="width: 70%;" v-if="isFullScreen">
        <img alt="fullscreen" src="/img/fullscreen_on.svg" style="width: 70%;" v-else>
      </button>
    </div>
  </div>
</template>

<script>
  export default {
    mounted() {
      this.video = this.$refs.video;
      this.player = this.$refs.player;
      this.progressWrapper = this.$refs.progress;
      document.addEventListener('fullscreenchange', () => {
        this.isFullScreen = !this.isFullScreen;
      });
    },

    data() {
      return {
        paused: true,
        duration: 0,
        currentTime: 0,
        isFullScreen: false
      }
    },

    props: {
      url: String,
      downloadUrl: String,
      contentType: String,
    },

    watch: {
      paused: function (paused) {
        if (paused) {
          this.video.pause()
        } else
          this.video.play()
      }
    },

    computed: {
      playBackBarWidth: function () {
        return (this.currentTime / this.duration * 100);
      }
    },

    methods: {
      handleTimeUpdate(e) {
        this.currentTime = e.target.currentTime;
      },

      handleLoadMetaData(e) {
        let video = e.target;
        this.duration = video.duration;
        this.$emit('videoChange', video)
      },

      format_seconds(seconds) {
        seconds = Math.floor(seconds);
        let minutes = Math.floor(seconds / 60);
        seconds = seconds - (minutes * 60);
        if (minutes < 10) {
          minutes = "0" + minutes;
        }
        if (seconds < 10) {
          seconds = "0" + seconds;
        }
        return minutes + ':' + seconds;
      },

      togglePlayerFullscreen() {
        if (this.isFullScreen) {
          document.exitFullscreen();
        } else {
          this.player.requestFullscreen();
        }
      },

      seek(event) {
        let wrapper = this.progressWrapper.getBoundingClientRect();
        let x = event.clientX - wrapper.left;
        let time = x / wrapper.width * this.duration;
        this.currentTime = time;
        this.video.currentTime = time;
      },

      addMoveListener(e) {
        this.progressWrapper.addEventListener('mousemove', this.seek)
      },

      removeListener(e) {
        this.progressWrapper.removeEventListener('mousemove', this.seek)
      }

    }
  }
</script>

<style>
  video:not(.fullscreen) {
    max-height: 60vh !important;
  }
  .progress {
    position: relative;
  }

  .player__progress {
    z-index: 999;
  }
</style>
