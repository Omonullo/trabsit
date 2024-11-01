<template>
  <div @drop.prevent="handleFileDrop">
    <input @change="handleFileInputChange" ref="file" style="display: none" type="file">
    <a @click="$refs.file.click()"
       :class="{'is-invalid':error}"
       @dragenter.prevent="dragenter=true"
       @dragover.prevent="dragenter=true"
       @dragleave.prevent="dragenter=false"
       class="h-100 dropzone kt-label-font-color-3"
       @drop.prevent="handleFileDrop">
      <h2 v-if="dragenter">{{"Отправить"|t}}</h2>
      <div v-else>
        <h3>{{"Загрузить"|t}}</h3>
        <span>
          {{"Перенесите или выберите файл для загрузки"|t}}
        </span>
        <div class="kt-font-warning mt-2">{{message}}</div>
        <div class="invalid-feedback">{{error}}</div>
      </div>
    </a>
  </div>
</template>

<script>
  export default {
    props: ['error', 'message'],
    data() {
      return {
        dragenter: false
      }
    },

    methods: {
      handleFileDrop(e) {
        this.$emit('change', e.dataTransfer.files[0])
      },
      handleFileInputChange(e) {
        this.$emit('change', e.target.files[0])
      }
    }
  }
</script>

<style>
  .dropzone {
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: transparent;
    width: 100%;
    height: 100%;
  }

  .dropzone.is-invalid {
    border-color: red!important;
  }

  .dropzone * {
    pointer-events: none;
  }
</style>
