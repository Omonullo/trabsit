<template>
  <div>
    <div class="row">
      <div class="col-md-6">
        <div class="form-group">
          <label>{{'Город или область'|t}}</label>
          <select :class="{'is-invalid':errors.area}"
                  class="form-control"
                  v-model="area">
            <option value=""></option>
            <option :value="area" v-for="area in areas">{{area.name}}</option>
          </select>
          <div class="invalid-feedback">
            {{errors.area}}
          </div>
        </div>
      </div>
      <div class="col-md-6">
        <div class="form-group">
          <label>{{"Район"|t}}</label>
          <select :class="{'is-invalid':errors.district}"
                  class="form-control"
                  v-model="district">
            <option value=""></option>
            <option :value='district'
                    v-for="district in districts">
              {{district.name}}
            </option>
          </select>
          <div class="invalid-feedback">
            {{errors.district}}
          </div>
        </div>
      </div>
    </div>
    <div class="form-group">
      <label>{{"Адрес"|t}}</label>
      <div class="kt-input-icon">
        <input :class="{'is-invalid':errors.address}"
               class="form-control"
               :placeholder="t('Поселок/Массив/Улица/Дом')"
               required type="text" v-model="address"/>
        <div class="invalid-feedback">
          {{errors.address}}
        </div>
      </div>
    </div>
    <div :class="{'is-invalid':errors.coords&&!manually}">
      <div id="map" style="min-height: 50vh"></div>
      <div class="invalid-feedback">{{errors.coords}}</div>
    </div>
    <label class="kt-checkbox my-4">
      <input type="checkbox" v-model="manually"> {{ "Ввести координаты вручную"|t }}
      <span></span>
    </label>
    <transition name="slide">
      <div :class="{'is-invalid':errors.coords}" class="row align-middle form-group" v-show="manually">
        <div class="col-md-6 col-sm-12">
          <div>
            <label>{{'Широта'|t}}</label>
            <input :class="{'is-invalid':errors.coords}"
                   :disabled="!manually"
                   class="form-control"
                   required type="number"
                   v-model='coords[0]'
                   v-on:focusout="moveMapCoords(coords, true)"/>
          </div>
        </div>
        <div class="col-md-6 col-sm-12">
          <div>
            <label>{{'Долгота'|t}}</label>
            <input :class="{'is-invalid':errors.coords}"
                   :disabled="!manually"
                   class="form-control"
                   required
                   type="number"
                   v-model='coords[1]' v-on:focusout="moveMapCoords(coords, true)"/>
          </div>
        </div>
        <div class="col-12 invalid-feedback">{{errors.coords}}</div>
      </div>
    </transition>
  </div>
</template>

<script>
  import axios from 'axios'

  export default {
    created() {
      window.init_map = () => {
        this.ymap = new ymaps.Map(document.getElementById('map'), {
          center: [41.2995, 69.2401],
          zoom: 13,
          controls: ['typeSelector']
        });

        this.marker = new ymaps.GeoObject(
          {
            geometry: {
              type: "Point",
              coordinates: this.coords
            }
          },
          {draggable: !this.manually}
        );

        this.marker.events.add("dragend", (event) => {
          // to solve bug
          this.coords = [...event.originalEvent.target.geometry.getCoordinates()];
          this.addressBindEnabled = false;
          this.dragged = !this.manually;
        });
        this.ymap.geoObjects.add(this.marker);
      };
    },

    props: {
      areas: Array
    },

    data() {
      return {
        hasDragged: false,
        manually: false,
        addressBindEnabled: true
      }
    },

    methods: {
      moveMapCoords(coordinates, moveCenter) {
        if (moveCenter)
          this.ymap && this.ymap.setCenter(coordinates);
        this.marker && this.marker.geometry.setCoordinates(coordinates);
      },

    },

    computed: {
      districts: function () {
        return (this.area && this.area.districts) || []
      },

      yAddress: function () {
        return this.$store.getters['details/yAddress'];
      },

      area: {
        get() {
          return this.$store.state.details.area;
        },
        set(area) {
          this.$store.commit('details/updateArea', area)
        }
      },

      address: {
        get() {
          return this.$store.state.details.address;
        },
        set(address) {
          this.$store.commit('details/updateAddress', address)
        }
      },

      district: {
        get() {
          return this.$store.state.details.district;
        },
        set(district) {
          this.$store.commit('details/updateDistrict', district)
        }
      },

      coords: {
        get() {
          return this.$store.state.details.coords;
        },
        set(coords) {
          this.$store.commit('details/updateCoords', coords)
        }
      },

      errors() {
        return this.$store.state.details.errors;
      }
    },

    watch: {
      manually: function (manually) {
        this.addressBindEnabled = false;
        this.ymap.geoObjects.get(0).options.set({draggable: !manually});
      },

      yAddress: function (newAddress) {
        if (newAddress && newAddress !== '' && this.addressBindEnabled) {
          ymaps.geocode(newAddress).then(result => {
            this.coords = [...result.geoObjects.get(0).geometry.getCoordinates()];
            this.moveMapCoords(this.coords, true);
          });
        }
      }
    }
  }
</script>
