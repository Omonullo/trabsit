import { useEffect, useState } from "react";
import { message } from "antd";
import axios, { cancelToken } from "./axios";
import t from "../lang";
import moment from "moment";
export { default as axios, cancelToken } from "./axios";

export const testLP = (lp, strict = false) => {
  return (
    /\d{2}[A-Z]\d{3}[A-Z]{2}/.test(lp) || // 00Z000ZZ
    /\d{5}[A-Z]{3}/.test(lp) || // 00000ZZZ
    /\d{6}[A-Z]{2}/.test(lp) || // 000000ZZ
    /[XDT][0-9]{6}/.test(lp) || // XDT000000
    /\d{2}[MH]\d{6}/.test(lp) || // 00MH000000
    /UN\d{4}/.test(lp) || // UN0000
    /\d{2}MX\d{4}/.test(lp) || //00MX0000
    /CMD\d{2}\d{2}/.test(lp) || // CMD0000
    /PAA\d{3}/.test(lp) // PAA000
  );
};

message.config({
  top: 40,
  duration: 4,
  maxCount: 1,
  rtl: false,
  // prefixCls: 'my-message',
});

const useFetchAsync = (params) => {
  const { url, method = "get", data = {} } = params;
  const [loading, setLoading] = useState(true);
  const [response, setResponse] = useState({});
  const [error, setError] = useState(null);
  const source = cancelToken();

  const fetchData = async () => {
    setLoading(true);
    try {
      const { data: response } = await axios({
        method,
        data,
        url,
        cancelToken: source.token,
      });

      setResponse(response);
      setLoading(false);
    } catch (err) {
      setLoading(false);
      setError(err.message);
    }
  };

  useEffect(() => {
    if (url) {
      fetchData();
      return () => source.cancel("Component unmounted");
    }
  }, [params]);

  return { loading, data: response, error };
};

function handleUrl(url) {
  window.location.pathname = url;
}

function indexByKey(arr = [], fieldName = "id") {
  let result = {};
  arr.forEach((item) => {
    if (item) {
      result[item[fieldName]] = item;
    }
  });
  return result;
}

function omitFieldNames({ data, fileds = [] }) {
  let result = {};
  Object.values(data).forEach((item) => {
    let offense = {};
    for (let x in item) {
      if (x === "id" && !item.removable && item.citizen_id) {
        offense[x] = item[x];
      } else if (!fileds.includes(x)) {
        offense[x] = item[x];
      }
    }
    result[item.id] = offense;
  });
  return result;
}

function toUpperCase(e) {
  const start = e.target.selectionStart;
  const end = e.target.selectionEnd;
  e.target.value = e.target.value.toUpperCase();
  e.target.setSelectionRange(start, end);
  return e;
}

function createErrors(offense) {
  let errors = [];
  if (!testLP(offense.vehicle_id, true)) {
    if(offense.status === "accepted"){
      errors.push({
        name: "vehicle_id",
        filed: "Davlat Raqami",
        value: "Davlat raqamini to'g'ri kiriting!",
      });
    }
  }
  if (!offense.status || offense.status === "created") {
    errors.push({
      name: "status",
      filed: "Holat",
      value: "Holatni belgilang!",
    });
  }
  if (offense.status === "accepted" && !offense.article_id) {
    errors.push({
      name: "article_id",
      filed: "Modda",
      value: "Moddani tanlang!",
    });
  }
  if (offense.status === "rejected" && !offense.response_id) {
    errors.push({
      name: "response_id",
      filed: "Sabab",
      value: "Rad etish sababini tanlang!",
    });
  }
  if (offense.status === "accepted" && !offense.vehicle_img) {
    errors.push({
      name: "vehicle_img",
      filed: "Avtomobil rasmi",
      value: "Avtomobil rasmini tanlang!",
    });
  }
  if (offense.status === "accepted" && !offense.vehicle_id_img) {
    errors.push({
      name: "vehicle_id_img",
      filed: "Davlat belgisi rasmi",
      value: "DRB rasmini tanlang!",
    });
  }

  return errors;
}

function getBase64Image(img) {
  const canvas = document.createElement("canvas");
  canvas.width = img.width;
  canvas.height = img.height;
  const ctx = canvas.getContext("2d");
  ctx.drawImage(img, 0, 0);
  const dataURL = canvas.toDataURL("image/png");
  return dataURL.replace(/^data:image\/(png|jpg);base64,/, "");
}

const getBase64FromUrl = async (url) => {
  const data = await fetch(url);
  const blob = await data.blob();
  return await new Promise((resolve) => {
    const reader = new FileReader();
    reader.readAsDataURL(blob);
    reader.onloadend = () => {
      const base64data = reader.result;
      resolve(base64data);
    };
  });
};

async function convertOffenceImage(offence) {
  let vehicleImages = {
    vehicle_id_img: offence.vehicle_id_img,
    vehicle_img: offence.vehicle_img,
  };

  if (offence.vehicle_id_img?.startsWith("https://")) {
    vehicleImages.vehicle_id_img = await getBase64FromUrl(
      offence.vehicle_id_img
    );
  }

  if (offence.vehicle_img?.startsWith("https://")) {
    vehicleImages.vehicle_img = await getBase64FromUrl(offence.vehicle_img);
  }

  return vehicleImages;
}

function lpDetectionToHumanReadableText(
  data = [],
  frame_id = 0,
  vehicle_id = ""
) {
  const findByPlate = data.find(
    (i) => i.frame_id === frame_id && i.text === vehicle_id
  );
  if (!findByPlate) {
    return "";
  }

  const {
    makeModelYear: { make, model },
    color: { name },
  } = { color: "", makeModelYear: { make: "", model: "" }, ...findByPlate.car };
  const carDetails = `${make || ""} ${model || ""} ${t(name || "")}`;

  return carDetails
    .split(" ")
    .map((item, index, arr) => {
      if (index < arr.length - 1) {
        return item.slice(0, 1).toUpperCase() + item.slice(1);
      } else {
        console.log(item, typeof item);
        return item ? `- ${item.toLowerCase()}` : "";
      }
    })
    .join(" ");
}

const canSaveOffense = (offense) => {
  return (
    (!offense.accept_time ||
      (offense.accept_time &&
        offense.failure_time &&
        offense.failure_message)) &&
    (!offense.reject_time ||
      (offense.reject_time &&
        moment().diff(moment(offense.reject_time), "days") < 30))
  );
};

export {
  useFetchAsync,
  handleUrl,
  omitFieldNames,
  indexByKey,
  message,
  toUpperCase,
  createErrors,
  getBase64Image,
  getBase64FromUrl,
  lpDetectionToHumanReadableText,
  canSaveOffense,
};
