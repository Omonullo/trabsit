import axios from 'axios'

function reset(state) {
  state.error = '';
  state.id = null;
  state.url = null;
  state.width = null;
  state.height = null;
  state.duration = null;
  state.filename = null;
  state.filetype = null;
  state.filesize = null;
  state.status = 'initial';
  state.uploadProgress = 0;
  state.cancelSource.cancel();
  state.cancelSource = axios.CancelToken.source();
}

function create() {
  const state = {
    id: null,
    url: null,
    type: null,
    width: null,
    height: null,
    filename: null,
    filesize: null,
    duration: null,
    downloadUrl: null,
    uploadProgress: 0,
    lastUploadUpdate: null,
    status: 'initial',
    cancelSource: axios.CancelToken.source(),
    error: ''
  };

  const getters = {
    errorCount(state) {
      if (state.error)
        return 1;
      else return 0
    },
  }


  const actions = {

    async uploadVideo({state, commit, dispatch}, file) {
      commit('updateError', '');
      commit('updateStatus', 'uploading');
      commit('updateFile', file);
      let formData = new FormData();
      let isCanceled = false;
      formData.append('video', file);
      let result;
      try {
        result = await axios.post('/api/citizen/report/video',
          formData,
          {
            headers: {'Content-Type': 'multipart/form-data'},
            onUploadProgress: progressEvent => {
              return commit('updateProgress', progressEvent.loaded)
            },
            cancelToken: state.cancelSource.token
          }
        );
        commit('updateStatus', 'uploaded');
      } catch (e) {
        result = e.response;
        isCanceled = axios.isCancel(e);
      }

      if (isCanceled) return;

      if (result && result.status) { // if not network error
        if (result.status === 200) {
          commit('updateStatus', 'uploaded');
          commit('finishUpload', result.data);
          dispatch('showNotification', {text: t('Видео отправлено')}, {root: true})
        } else {
          commit('reset');
          commit('updateError', Object.values(result.data.errors.video).filter(e => e).join(', '))
        }
      } else {
        commit('reset');
        commit('updateError', "Ошибка: проверьте соединение")
        dispatch('showNotification', {text: t("Сервис недоступен, проверьте соединение"), type: "danger"}, {root: true})
      }
    }
  };

  const mutations = {
    updateVideo(state, video) {
      state.duration = video.duration;
      state.height = video.videoHeight;
      state.width = video.videoWidth;
      state.error = '';
    },

    updateError(state, error) {
      state.error = error;
    },

    updateFile(state, {size, name, type}) {
      state.filename = name;
      state.filesize = size;
      state.filetype = type;
    },

    updateStatus(state, status) {
      state.status = status;
    },

    finishUpload(state, {id, url, 'download-url': download_url}) {
      state.id = id;
      state.url = url;
      state.downloadUrl = download_url;
    },

    updateProgress(state, progress) {
      state.uploadProgress = progress;
    },

    updateType(state, type) {
      state.type = type;
    },

    reset,
  };


  return {
    namespaced: true,
    state,
    getters,
    actions,
    mutations
  }
}

export default create;
