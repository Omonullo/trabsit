<template>
  <span class="kt-font-danger" v-if="warn">{{warn}}</span>
  <span class="kt-font-info" v-else-if="text">{{text}}</span>
  <span class="kt-font-danger" v-else>{{"Не указано"|t}}</span>
</template>
<script>
  export default {
    props: {
      text: String,
      warn: String
    }
  }
</script>
