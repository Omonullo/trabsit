import * as types from '../actionTypes';

export const showImageCropper = () => {
  return {
    type: types.SHOW_IMAGE_CROPPER,
  }
};

export const selectImage = ({ data }) => {
  return {
    type: types.SET_VEHICLE_IMAGE,
    data,
  }
};

export const selectVehicleData = ({ vehicle_id, imageType, id }) => {
  return {
    type: types.SELECT_VEHICLE_DATA,
    vehicle_id,
    imageType,
    id,
  }
};

export const hideImageCropper = () => {
  return {
    type: types.HIDE_IMAGE_CROPPER
  }
};

export const clearImageCropper = () => {
  return {
    type: types.CLEAR_VEHICLE_IMAGE
  }
};