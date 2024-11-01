<template>
  <div>
    <div class="d-flex justify-content-between">
      <div>{{this.uploadSpeed|humanFileSize}}/s</div>
      <div>{{secondsLeft|humanRemainingTime}}</div>
    </div>
    <div class="progress">
      <div :style="{width: percentage}" class="progress-bar progress-bar-striped progress-bar-animated"></div>
    </div>
    <h5 class="text-center mt-2">{{"Загрузка"|t}} {{percentage}}</h5>

  </div>

</template>

<script>
  export default {
    props: ['uploaded', 'filesize'],

    mounted() {
      let uploaded = this.uploaded;
      this.speedCheckInterval = setInterval(() => {
        this.uploadSpeed = (this.uploaded - uploaded);
        uploaded = this.uploaded
      }, 1000)
    },

    destroyed() {
      clearInterval(this.speedCheckInterval);
    },

    data() {
      return {
        speedCheckInterval: null,
        uploadSpeed: 0
      }
    },

    computed: {
      percentage() {
        return Math.round((this.uploaded / this.filesize) * 100) + '%'
      },

      secondsLeft() {
        return Math.round((this.filesize - this.uploaded) / this.uploadSpeed);
      },
    }
  }

</script>
