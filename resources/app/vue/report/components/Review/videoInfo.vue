<template>
  <div>
    {{ "Имя файла"|t }}:
    <Info :text="filename"></Info>
    <br/>
    {{ "Размер"|t }}:
    <Info :text="filesize|humanFileSize"></Info>
    <div v-if="error">
      {{ "Ошибки"|t }}:
      <Info :warn="error"></Info>
    </div>
  </div>
</template>
<script>
  import Info from './info.vue'

  export default {
    components: {Info},
    props: {
      width: Number,
      height: Number,
      filename: String,
      duration: Number,
      filesize: Number,
      error: String
    },
    computed: {
      resolution() {
        const width = this.width;
        const height = this.height;
        return width && height && `${width} x ${height}`
      },
      durationStr() {
        if (!!this.duration)
          return t(`%d секунд`, this.duration)
      }

    }
  }
</script>
