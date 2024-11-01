import * as types from '../actionTypes';

const initialState = {
  isActive: false,
  vehicle_id: '',
  vehicle_img: '',
  vehicle_id_img: '',
  imageType: 'vehicel_img',
  ready: false,
  id: null,
  isCropperActive: false,
};

const reducer = (state = initialState, action) => {
  switch (action.type) {
    case types.SET_VEHICLE_IMAGE: {
      return {
        ...state,
        ready: true,
        isActive: false,
        isCropperActive: false,
        ...action.data,
      }
    }
    case types.SELECT_VEHICLE_DATA: {
      return {
        ...state,
        isActive: true,
        ready: false,
        vehicle_id: action.vehicle_id,
        imageType: action.imageType,
        id: action.id,
      }
    }
    case types.SHOW_IMAGE_CROPPER: {
      return {
        ...state,
        isActive: true,
        isCropperActive: true,
      }
    }
    case types.HIDE_IMAGE_CROPPER: {
      return { ...state, isCropperActive: false };
    }
    case types.CLEAR_VEHICLE_IMAGE: {
      return initialState;
    }
    default: {
      return state;
    }
  }
};

export default reducer;
