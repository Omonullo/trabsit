<template>
  <div>
    <LocationForm :areas="areas"></LocationForm>
    <TimeForm></TimeForm>
  </div>
</template>
<script>
  import LocationForm from './Location.vue'
  import TimeForm from './Time.vue'
  export default {
    components: {LocationForm, TimeForm},
    created() {
      this.areas = window.areas
    }
  }
</script>
