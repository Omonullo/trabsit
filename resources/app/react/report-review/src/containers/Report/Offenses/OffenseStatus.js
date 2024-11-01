import React, { useMemo, useContext, useRef } from "react";
import { Select, Col, Checkbox, Input, Button } from "antd";
import t, { getLanguage } from "../../../lang";
import moment from "moment";
import ErrorMessage from "./ErrorMessage";
import { createErrors, indexByKey, canSaveOffense } from "../../../utils";
import { useDispatch } from "react-redux";
import { selectVehicleData } from "../../../store/offenseImage/actions";
import { AiOutlineVideoCamera } from "react-icons/ai";
import defaultCarImage from "../../../assets/car.svg";
import defaultImage from "../../../assets/picture.svg";
import ReportContext from "../../../context/ReportContext";

export default function OffenseStatus({
  articleVisible,
  handleCheck,
  handleInput,
  handleSelect,
  id,
  responseVisible,
  videoTimestamps = [],
  extraVideoTimestamps = [],
  setArticleVisible,
  setResponseVisible,
  status,
  values,
  vehicle_id,
  vehicle_id_img,
  vehicle_img,
  setImageVisible,
  setActiveImageIndex,
}) {
  const reportContext = useContext(ReportContext);

  const {
    articles = [],
    responses = [],
    applyRejectionToAll,
    videoRef,
    extraVideoRef,
    tab,
    videoDetectionsData,
    extraVideoDetectionsData,
  } = reportContext;
  const videoFps = videoDetectionsData?.fps || 30;
  const extraVideoFps = extraVideoDetectionsData?.fps || 30;

  const currentFps = tab === "video" ? videoFps : extraVideoFps;
  const currentVideoRef = tab === "video" ? videoRef : extraVideoRef;
  const currentTimestamps =
    tab === "video" ? videoTimestamps : extraVideoTimestamps;

  const lang = getLanguage();
  const errors = useMemo(() => indexByKey(createErrors(values), "name"), [
    values,
  ]);
  const dispatch = useDispatch();

  const offenseForm = values;
  const canStatusBeChanged = canSaveOffense(offenseForm);

  const sortedResponsByPriority = useMemo(() => {
    return responses.sort((a, b) => {
      return a.priority - b.priority;
    });
  }, [responses]);

  const articlesByID = useMemo(() => {
    let articleObj = {};
    articles.forEach((item) => {
      const title =
        item[`alias_${lang}`] ||
        item.alias_uz_la ||
        item.alias_uz_cy ||
        item.alias_ru;
      const text =
        item[`text_${lang}`] ||
        item.text_uz_la ||
        item.text_uz_cy ||
        item.text_ru;
      articleObj[item.id] = { ...item, title, text, ...item };
    });
    return articleObj;
  }, [articles, lang]);

  const responsesByID = useMemo(() => {
    let responsObj = {};
    responses.forEach((item) => {
      const title =
        item[`alias_${lang}`] ||
        item.text_uz_la ||
        item.text_uz_cy ||
        item.text_ru;
      responsObj[item.id] = { ...item, title };
    });
    return responsObj;
  }, [responses, lang]);

  const selectImageHandler = (imageType) => {
    const otherTimestamps =
      tab === "video" ? extraVideoTimestamps : videoTimestamps;
    const otherVideoRef = tab === "video" ? extraVideoRef : videoRef;
    const otherFps = tab === "video" ? extraVideoFps : videoFps;

    if (currentTimestamps[0]) {
      currentVideoRef.current.currentTime = currentFps
        ? Math.round(currentTimestamps[0] / currentFps)
        : 0;

      currentVideoRef?.current?.scrollIntoView({
        block: "start",
        behavior: "smooth",
      });
    } else if (otherTimestamps[0]) {
      otherVideoRef.current.currentTime = currentFps
        ? Math.round(otherTimestamps[0] / otherFps)
        : 0;
      otherVideoRef?.current?.scrollIntoView({
        block: "start",
        behavior: "smooth",
      });
    } else {
      currentVideoRef?.current?.scrollIntoView({
        block: "start",
        behavior: "smooth",
      });
    }
    dispatch(selectVehicleData({ vehicle_id, imageType, id }));
  };
  const articleSelectRef = useRef(null);
  const responseSelectRef = useRef(null);
  if (status === "rejected") {
    return (
      <>
        <Col className="label mt-3" md={6}>
          <label className={errors.response_id ? "has-error" : ""}>
            {t("Rad etish sababi")}
          </label>
        </Col>
        <Col md={18} className="d-flex flex-column mt-3">
          <Select
            ref={responseSelectRef}
            onFocus={() => setResponseVisible(true)}
            onBlur={() => setResponseVisible(false)}
            open={responseVisible}
            placeholder={t("Rad etish sababini tanlang")}
            onSelect={(val) => handleSelect("response_id", val)}
            className="select-input"
            value={values.response_id}
            allowClear
            size="large"
            showSearch
            disabled={!canStatusBeChanged}
            style={{
              width: "100%",
              display:
                responseVisible || !values.response_id ? "block" : "none",
            }}
            filterOption={(input, option) => {
              return (
                option.label.toLowerCase().indexOf(input.toLowerCase()) >= 0
              );
            }}
          >
            {sortedResponsByPriority.map((item) => {
              const title = `${item.number} - ${item[`alias_${lang}`]}`;
              return (
                <Select.Option key={item.id} value={item.id} label={title}>
                  {title}
                </Select.Option>
              );
            })}
          </Select>
          {values.response_id && !responseVisible ? (
            <div
              className="selected-article"
              onClick={() => {
                if (canStatusBeChanged) {
                  setResponseVisible(true);
                  setTimeout(() => {
                    responseSelectRef.current.focus();
                  });
                }
              }}
            >
              <div className="article-number">
                {responsesByID[values.response_id]?.number}
              </div>
              <div>
                {responsesByID[values.response_id]?.title}
                <p>{responsesByID[values.response_id]?.[`text_${lang}`]}</p>
              </div>
            </div>
          ) : null}
          <ErrorMessage errors={errors} fieldName={"response_id"} />
          <Checkbox
            disabled={!canStatusBeChanged}
            name="extraInfo"
            onChange={handleCheck}
            checked={values.extraInfo}
            style={{ marginTop: 5 }}
          >
            {t(
              "Rad etish sababini tushuntirish uchun qo'shimcha matnni kiritish"
            )}
          </Checkbox>

          {values.extraInfo ? (
            <Input.TextArea
              disabled={!canStatusBeChanged}
              className="mt-2 mb-2"
              name="extra_response"
              onChange={handleInput}
              value={values.extra_response}
              placeholder={t(
                "Rad etish sababini tushuntirish uchun qo'shimcha matn"
              )}
            />
          ) : null}
          <Button
            style={{ marginTop: 7 }}
            onClick={() =>
              applyRejectionToAll({
                response_id: values.response_id,
                description: values.description,
                extraInfo: values.extraInfo,
              })
            }
            disabled={!canStatusBeChanged}
          >
            {t("Qolganiga ham shu rad etish sababi")}
          </Button>
        </Col>
      </>
    );
  }

  if (status === "accepted") {
    return (
      <>
        <Col className="label mt-3" md={6}>
          <label className={errors.article_id ? "has-error" : ""}>
            {t("Modda")}
          </label>
        </Col>
        <Col md={18} className="col-left mt-3">
          <Select
            ref={articleSelectRef}
            onBlur={() => setArticleVisible(false)}
            onFocus={() => setArticleVisible(true)}
            open={articleVisible}
            placeholder={t("Moddani tanlang")}
            onSelect={(val) => handleSelect("article_id", val)}
            className="select-input"
            value={values.article_id}
            allowClear
            size="large"
            showSearch
            disabled={!canStatusBeChanged}
            style={{
              width: "100%",
              display: articleVisible || !values.article_id ? "block" : "none",
            }}
            filterOption={(input, option) => {
              return (
                option.props.label.toLowerCase().indexOf(input.toLowerCase()) >=
                0
              );
            }}
          >
            {articles.map((item) => {
              const title = `${item.number} - ${item[`alias_${lang}`]}`;
              return (
                <Select.Option key={item.id} value={item.id} label={title}>
                  {title}
                </Select.Option>
              );
            })}
          </Select>
          {values.article_id && !articleVisible ? (
            <div
              className="selected-article"
              onClick={() => {
                if (canStatusBeChanged) {
                  setArticleVisible(true);
                  setTimeout(() => {
                    articleSelectRef.current.focus();
                  });
                }
              }}
            >
              <div className="article-number">
                {articlesByID[values.article_id].number}
                <div className="factor">
                  {articlesByID[values.article_id].factor} - {t("BHM")}
                </div>
              </div>
              <div>
                {articlesByID[values.article_id]?.title}
                <p>{articlesByID[values.article_id]?.[`text_${lang}`]}</p>
              </div>
            </div>
          ) : null}
          <ErrorMessage errors={errors} fieldName={"article_id"} />
        </Col>
        {values.status === "accepted" ? (
          <>
            <Col className="label mt-3" md={6}>
              <label
                className={
                  errors.vehicle_id_img || errors.vehicle_img ? "has-error" : ""
                }
              >
                {t("Suratlar")}
              </label>
            </Col>
            <Col md={18} className="col-left mt-3 vehicle-images">
              <div className="d-flex">
                {vehicle_img ? (
                  <div className="empty-image clickable">
                    {/* <SRLWrapper options={SRLoptions}> */}
                    <img
                      src={vehicle_img}
                      alt="vehicle"
                      onClick={() => {
                        setImageVisible();
                        setActiveImageIndex(0);
                      }}
                    />
                    {/* </SRLWrapper> */}
                    <div>
                      <Button
                        size="small"
                        className="take-image"
                        disabled={!canStatusBeChanged}
                        onClick={() => selectImageHandler("vehicle_img")}
                      >
                        <AiOutlineVideoCamera />
                        {t("Rasm tanlash")}
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div
                    className="empty-image clickable"
                    onClick={() =>
                      canStatusBeChanged && selectImageHandler("vehicle_img")
                    }
                  >
                    <img src={defaultCarImage} alt="" />
                    <ErrorMessage errors={errors} fieldName="vehicle_img" />
                  </div>
                )}
              </div>
              <div className="d-flex">
                {vehicle_id_img ? (
                  <div className="empty-image clickable">
                    {/* <SRLWrapper options={SRLoptions}> */}
                    <img
                      src={vehicle_id_img}
                      alt="vehicle"
                      onClick={() => {
                        setImageVisible();
                        setActiveImageIndex(1);
                      }}
                    />
                    {/* </SRLWrapper> */}
                    <div>
                      <Button
                        size="small"
                        className="take-image"
                        disabled={!canStatusBeChanged}
                        onClick={() => selectImageHandler("vehicle_id_img")}
                      >
                        <AiOutlineVideoCamera />
                        {t("Rasm tanlash")}
                      </Button>
                    </div>
                  </div>
                ) : (
                  <div
                    className="empty-image clickable"
                    onClick={() =>
                      canStatusBeChanged && selectImageHandler("vehicle_id_img")
                    }
                  >
                    <img src={defaultImage} alt="" />
                    <ErrorMessage errors={errors} fieldName="vehicle_id_img" />
                  </div>
                )}
              </div>
            </Col>
          </>
        ) : null}
      </>
    );
  }

  return null;
}
