import moment from "moment";
import * as R from "ramda";
import React, {
  useEffect,
  useMemo,
  useState,
  useRef,
  useContext,
  useLayoutEffect,
  useCallback,
} from "react";
import { Slider, Spin, Tooltip, Switch, Radio, Tabs } from "antd";
import StyledComponent, { Player } from "./style";
import t from "../../lang";
import {
  AiOutlineCamera,
  AiOutlineDownload,
  AiOutlinePause,
  AiOutlinePlaySquare,
  AiOutlineVideoCamera,
} from "react-icons/ai";
import { IoIosArrowForward, IoIosArrowBack } from "react-icons/io";
import ReportContext from "../../context/ReportContext";
import OffenseModal from "./Offenses/OffenseModal";
import { Suspense } from "react";
import useMeasure from "react-use-measure";
import VideoStatus from "./VideoStatus";
import CropImage from "./CropImage";
import { useSelector } from "react-redux";
import { useDispatch } from "react-redux";
import {
  clearImageCropper,
  showImageCropper,
} from "../../store/offenseImage/actions";
import { testLP } from "../../utils";

const drawDetection = (
  canvas,
  [x, y, width, height],
  label,
  [boxColor, textBgColor, textColor] = ["#ecff00", "#ecff00", "#333"]
) => {
  const ctx = canvas.getContext("2d");
  ctx.lineWidth = 5;
  ctx.strokeStyle = boxColor;
  ctx.strokeRect(x, y, width, height);

  const textPadding = 5;

  const textMeasurements = ctx.measureText(label);
  const textWidth = textMeasurements.width;
  const textHeight =
    textMeasurements.fontBoundingBoxAscent +
    textMeasurements.fontBoundingBoxDescent;

  ctx.fillStyle = textBgColor;
  ctx.fillRect(
    x - textPadding,
    y - textHeight - textPadding,
    Math.max(width, textWidth) + textPadding + 7,
    textHeight + textPadding
  );

  ctx.fillStyle = textColor;
  ctx.lineWidth = 3;
  ctx.font = "17px verdana";
  ctx.fillText(label, x, y - 5);
};

function warpedBoxToRectBox(warpedBox = []) {
  const [
    leftTopX,
    leftTopY,
    rightTopX,
    rightTopY,
    rightBottomX,
    rightBottomY,
    leftBottomX,
    leftBottomY,
  ] = warpedBox;
  const x1 = Math.min(leftTopX, leftBottomX);
  const y1 = Math.min(leftTopY, rightTopY);
  const x2 = Math.max(rightTopX, rightBottomX);
  const y2 = Math.max(leftBottomY, rightBottomY);
  return [x1, y1, x2 - x1, y2 - y1];
}

function getObjectFitSize(
  contains /* true = contain, false = cover */,
  containerWidth,
  containerHeight,
  width,
  height
) {
  let doRatio = width / height;
  let cRatio = containerWidth / containerHeight;
  let targetWidth = 0;
  let targetHeight = 0;
  let test = contains ? doRatio > cRatio : doRatio < cRatio;

  if (test) {
    targetWidth = containerWidth;
    targetHeight = targetWidth / doRatio;
  } else {
    targetHeight = containerHeight;
    targetWidth = targetHeight * doRatio;
  }

  return {
    width: targetWidth,
    height: targetHeight,
    x: (containerWidth - targetWidth) / 2,
    y: (containerHeight - targetHeight) / 2,
  };
}

const CropperPopupSlider = ({
  imageType,
  isActive,
  isCropperActive,
  handleVideoSliderChanges,
  playerTimeInPercents,
  playerTime,
  containerRef,
  handleHideCropper,
  vehicle_id,
  setPlaying,
  handleForward,
}) => {
  const dispatch = useDispatch();
  const title =
    imageType === "vehicle"
      ? t("Transport rasmini tanlang")
      : t("DRB rasmini tanlang");
  return isActive && !isCropperActive ? (
    <>
      <Slider
        min={1}
        max={100}
        onChange={handleVideoSliderChanges}
        value={playerTimeInPercents}
        className="duration-slider"
        tipFormatter={(val) => (
          <span>
            {moment.utc((playerTime / 100) * val || 0).format("mm:ss")}
          </span>
        )}
      />

      <Slider
        min={1}
        max={100}
        // onChange={handleVideoSliderChanges}
        value={playerTimeInPercents}
        getTooltipPopupContainer={() => containerRef.current}
        className="duration-slider"
        tooltipVisible
        style={{
          top: 15,
          zIndex: -1,
          position: "absolute",
        }}
        tooltipPlacement="bottom"
        tipFormatter={(val) => (
          <div
            className="slider-container"
            onClick={(e) => e.stopPropagation()}
          >
            <button className="close-btn" onClick={handleHideCropper}>
              x
            </button>
            <div>
              <b>{vehicle_id}</b>
            </div>
            <p>{title}</p>
            <Radio.Group size="large" className="d-flex controller">
              <Radio.Button
                value="fastBackward"
                onClick={() => handleForward(-0.6)}
              >
                <IoIosArrowBack />
                <IoIosArrowBack />
              </Radio.Button>
              <Radio.Button
                value="backward"
                onClick={() => handleForward(-0.03)}
              >
                <IoIosArrowBack />
              </Radio.Button>
              <Radio.Button
                value="small"
                onClick={() => {
                  setPlaying(false);
                  dispatch(showImageCropper());
                }}
              >
                <AiOutlineCamera
                  size={30}
                  color="#09bb87"
                  // className="clickable"
                />
              </Radio.Button>
              <Radio.Button value="forward" onClick={() => handleForward(0.03)}>
                <IoIosArrowForward />
              </Radio.Button>
              <Radio.Button
                value="fastForward"
                onClick={() => handleForward(0.6)}
              >
                <IoIosArrowForward />
                <IoIosArrowForward />
              </Radio.Button>
            </Radio.Group>
          </div>
        )}
      />
    </>
  ) : (
    <Slider
      min={1}
      max={100}
      onChange={handleVideoSliderChanges}
      value={playerTimeInPercents}
      className="duration-slider"
      tipFormatter={(val) => (
        <span>{moment.utc((playerTime / 100) * val || 0).format("mm:ss")}</span>
      )}
    />
  );
};

const measureOptions = {
  scroll: true,
};

function updateCanvas(
  mediaTime,
  setPlayerTime,
  visibleVideoRef,
  detectionsData,
  videoCanvasRef,
  lastFrameNumber,
  updateCanvas
) {
  const currentTime = mediaTime || 0;
  // rerender will occur, when the difference is greater than .2
  setPlayerTime((playerTimer) => {
    return Math.abs(currentTime - playerTimer) > 0.2 ||
      visibleVideoRef.current?.paused
      ? currentTime
      : playerTimer;
  });

  const videoCanvas = videoCanvasRef.current;
  if (!videoCanvas) return;

  videoCanvas
    ?.getContext("2d")
    .drawImage(
      visibleVideoRef.current,
      0,
      0,
      videoCanvas.width,
      videoCanvas.height
    );

  if (Object.keys(detectionsData || {}).length > 0) {
    const {
      fps,
      detections,
      height: originalHeight,
      width: originalWidth,
    } = detectionsData;
    const frameNumber = Math.round(currentTime * fps);
    const expectedFrame = JSON.parse(
      visibleVideoRef.current.dataset.expectedFrame || "-1"
    );
    const isExpectationSatisfied = JSON.parse(
      visibleVideoRef.current.dataset.expectationSatisfied || "true"
    );

    if (!isExpectationSatisfied) {
      if (
        Math.abs(visibleVideoRef.current.dataset.expectedFrame - frameNumber) >
        10
      ) {
        visibleVideoRef.current.currentTime =
          visibleVideoRef.current.dataset.expectedFrame * fps - 0.2;
      }

      if (frameNumber === expectedFrame) {
        visibleVideoRef.current.pause();
        visibleVideoRef.current.dataset.expectationSatisfied = "true";
      } else {
        visibleVideoRef.current.play();
      }
    }

    lastFrameNumber.current = frameNumber;
    // const foundDetection = R.pipe(
    //   R.identity,
    //   R.slice(
    //     R.max(0, frameNumber - 2),
    //     R.min(frameNumber + 2, detections.length)
    //   ),
    //   R.append(detections[frameNumber]),
    //   R.sortBy(R.compose(R.length, R.prop("plates"))),
    //   R.last
    // )(detections);
    const foundDetection = detections[frameNumber];
    foundDetection?.plates?.forEach((plate) => {
      const originalPlateBox = warpedBoxToRectBox(plate.warpedBox);
      const convertedPlateBox = [
        (originalPlateBox[0] / originalWidth) * videoCanvas.width,
        (originalPlateBox[1] / originalHeight) * videoCanvas.height,
        (originalPlateBox[2] / originalWidth) * videoCanvas.width,
        (originalPlateBox[3] / originalHeight) * videoCanvas.height,
      ];
      drawDetection(videoCanvas, convertedPlateBox, plate.text, [
        "#ecff00",
        "#ecff00",
        "#000",
      ]);

      if (plate.car) {
        const originalCarBox = warpedBoxToRectBox(plate.car.warpedBox);
        const convertedCarBox = [
          (originalCarBox[0] / originalWidth) * videoCanvas.width,
          (originalCarBox[1] / originalHeight) * videoCanvas.height,
          (originalCarBox[2] / originalWidth) * videoCanvas.width,
          (originalCarBox[3] / originalHeight) * videoCanvas.height,
        ];
        const { makeModelYear, color } = plate.car || {};
        drawDetection(
          videoCanvas,
          convertedCarBox,
          [makeModelYear?.make, makeModelYear?.model, t(color?.name)]
            .filter((s) => s)
            .map((item, index, arr) => {
              if (index < arr.length - 1) {
                return item.slice(0, 1).toUpperCase() + item.slice(1);
              } else {
                return `- ${item.toLowerCase()}`;
              }
            })
            .join(" "),
          ["#37ff00", "#383838", "#c4c4c4"]
        );
      }
    });
  }
  return visibleVideoRef.current.requestVideoFrameCallback(updateCanvas);
}

function fixDigitError(lp) {
  return lp.replace(/O/g, "0").replace(/A/g, "4").replace(/B/g, "8");
}

function fixCharsError(lp) {
  return lp.replace(/0/g, "O").replace(/4/g, "A").replace(/8/g, "B");
}

function correctLP(lp) {
  if (!lp) {
    return lp;
  }
  if (!testLP(lp)) {
    /*
    -- 00#0 00000 - 1 888 cases
    * */

    // debugger;
    if (lp.length === 9) {
      const newLp = fixDigitError(lp);
      if (testLP(newLp)) {
        return newLp;
      }
    }
    /*
    -- 00Z0 00ZZ  - 274 609 cases
    -- 0000 0ZZZ  - 44 853 cases
    * */
    if (lp.length === 8) {
      const [O0, O1, O2, O3, O4, O5, O6, O7] = fixDigitError(lp);
      const [Z0, Z1, Z2, Z3, Z4, Z5, Z6, Z7] = fixCharsError(lp);

      // vv v
      // 00Z0 00ZZ
      let newLp = R.pipe(
        R.split(""),
        R.assocPath([0], O0),
        R.assocPath([1], O1),
        R.assocPath([3], O3),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }
      //   v
      // 00Z0 00ZZ
      // 0000 0ZZZ
      newLp = R.pipe(R.split(""), R.assocPath([2], O2), R.join(""))(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      newLp = R.pipe(R.split(""), R.assocPath([2], Z2), R.join(""))(lp);
      if (testLP(newLp)) {
        return newLp;
      }
      //        vv
      // 00Z0 00ZZ
      // 0000 0ZZZ
      newLp = R.pipe(
        R.split(""),
        R.assocPath([6], Z6),
        R.assocPath([7], Z7),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      //      vv
      // 00Z0 00ZZ
      // 0000 0ZZZ
      newLp = R.pipe(
        R.split(""),
        R.assocPath([4], O4),
        R.assocPath([5], O5),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      //      vv
      // 0000 0ZZZ
      // 00Z0 00ZZ
      newLp = R.pipe(
        R.split(""),
        R.assocPath([4], O4),
        R.assocPath([5], Z5),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      //   v    vv
      // 00Z0 00ZZ
      newLp = R.pipe(
        R.split(""),
        R.assocPath([2], Z2),
        R.assocPath([6], Z6),
        R.assocPath([7], Z7),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      // vv vvv
      // 00Z000ZZ
      newLp = R.pipe(
        R.split(""),
        R.assocPath([0], O0),
        R.assocPath([1], O1),
        R.assocPath([3], O3),
        R.assocPath([4], O4),
        R.assocPath([5], O5),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      // vvvvvvvv
      // 00Z000ZZ
      newLp = `${O0}${O1}${Z2}${O3}${O4}${O5}${Z6}${Z7}`;
      if (testLP(newLp)) {
        return newLp;
      }

      // vvvvv
      // 00000ZZZ

      newLp = R.pipe(
        R.split(""),
        R.assocPath([0], O0),
        R.assocPath([1], O1),
        R.assocPath([2], O2),
        R.assocPath([3], O3),
        R.assocPath([4], O4),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      //      vvv
      // 00000ZZZ
      newLp = R.pipe(
        R.split(""),
        R.assocPath([5], Z5),
        R.assocPath([6], Z6),
        R.assocPath([7], Z7),
        R.join("")
      )(lp);
      if (testLP(newLp)) {
        return newLp;
      }

      // vvvvvvvv
      // 00000ZZZ
      newLp = `${O0}${O1}${O2}${O3}${O4}${Z5}${Z6}${Z7}`;
      if (testLP(newLp)) {
        return newLp;
      }
    }
  }
  return lp;
}

export default function VideoPlayer() {
  const reportContext = useContext(ReportContext);
  const {
    tab,
    setTab,
    videoDetectionsData,
    extraVideoDetectionsData,
  } = reportContext;
  const hasExtraVideo = !!reportContext.report.extra_video;
  const lastVideoFrameNumber = useRef(0);
  const lastExtraVideoFrameNumber = useRef(0);
  const currentLastFrameNumber =
    tab === "video" ? lastVideoFrameNumber : lastExtraVideoFrameNumber;
  const [offenseModalVisible, setOffenseModalVisible] = useState(false);

  const [videoPlaying, setVideoPlaying] = useState(false);
  const [extraVideoPlaying, setExtraPlaying] = useState(false);

  const playing = tab === "video" ? videoPlaying : extraVideoPlaying;

  const currentVideoRef =
    tab === "video" ? reportContext.videoRef : reportContext.extraVideoRef;

  const setPlaying = useCallback(
    /**  @param {boolean}  state  */
    (state) => {
      if (tab === "video") {
        setVideoPlaying(state);
      } else {
        setExtraPlaying(state);
      }

      if (state) {
        currentVideoRef.current?.play();
      } else {
        currentVideoRef.current?.pause();
      }
    },
    [tab, currentVideoRef]
  );

  const [videoPlayerTime, setVideoPlayerTime] = useState(0);
  const [extraVideoPlayerTime, setExtraVideoPlayerTime] = useState(0);

  const currentPlayerTime =
    tab === "video" ? videoPlayerTime : extraVideoPlayerTime;
  const setCurrentPlayerTime =
    tab === "video" ? setVideoPlayerTime : setExtraVideoPlayerTime;

  const currentDetectionsData =
    tab === "video" ? videoDetectionsData : extraVideoDetectionsData;

  const currentVideoUrl = reportContext.report[tab]?.url;

  const videoUrl = reportContext.report.video?.url;
  const extraVideoUrl = reportContext.report.extra_video?.url;
  const currentDownloadUrl = reportContext.report[tab]?.["download-url"];
  const currentEncoderStatus = reportContext.report[`${tab}_encoder_status`];
  const currentDetectorStatus = reportContext.report[`${tab}_detector_status`];

  const containerRef = useRef(null);
  const [detectionsVisible, setDetectionsVisible] = useState(true);
  const videoCanvasRef = useRef();

  const [videoMeasureRef, videoRect] = useMeasure(measureOptions);

  const [extraVideoMeasureRef, extraVideoRect] = useMeasure(measureOptions);

  const adjustedVideoRect = useMemo(() => {
    const rect = tab === "video" ? videoRect : extraVideoRect;
    return {
      ...rect,
      ...(currentVideoRef.current &&
        getObjectFitSize(
          true,
          rect.width,
          rect.height,
          currentVideoRef.current?.videoWidth,
          currentVideoRef.current?.videoHeight
        )),
    };
  }, [videoRect, extraVideoRect, tab, currentVideoRef.current]);

  const [offenseModalData, setOffenseModalData] = useState({
    vehicle_id: "",
    frame_id: null,
    vehicle_img: "",
    vehicle_id_img: "",
    color: "",
    model: "",
    make: "",
  });

  const dispatch = useDispatch();
  // TODO remove redux
  const { isActive, isCropperActive, imageType, vehicle_id } = useSelector(
    (state) => state.offenseImage || { isActive: false }
  );

  const handleVideoSliderChanges = (val = 1) => {
    const newTime = (currentVideoRef.current.duration / 100) * val;
    setCurrentPlayerTime(newTime);
    currentVideoRef.current && (currentVideoRef.current.currentTime = newTime);
    setPlaying(false);
  };

  const playerTimeInPercents = currentVideoRef.current?.duration
    ? Math.round((currentPlayerTime / currentVideoRef.current?.duration) * 100)
    : 0;

  const handleForward = (amount) => {
    const newTime = currentVideoRef.current?.currentTime + amount;
    setCurrentPlayerTime(newTime);
    currentVideoRef.current.currentTime = newTime;
    setPlaying(false);
  };

  const handleBackward = (amount) => {
    const newTime = currentVideoRef.current?.currentTime - amount;
    setCurrentPlayerTime(newTime);
    currentVideoRef.current && (currentVideoRef.current.currentTime = newTime);
    setPlaying(false);
  };

  const handleClick = (event) => {
    const videoCanvas = videoCanvasRef.current;
    const ctx = videoCanvas?.getContext("2d");
    const rect = videoCanvas.getBoundingClientRect();

    let clickX = event.clientX - rect.left;
    let clickY = event.clientY - rect.top;
    const frameDetection =
      currentDetectionsData?.detections?.[currentLastFrameNumber.current];

    const {
      width: originalWidth,
      height: originalHeight,
    } = currentDetectionsData;
    const detectedPlates =
      frameDetection?.plates
        ?.map((plate) => {
          const originalPlateBox = warpedBoxToRectBox(plate.warpedBox);

          const convertedPlateBox = [
            (originalPlateBox[0] / originalWidth) * videoCanvas.width,
            (originalPlateBox[1] / originalHeight) * videoCanvas.height,
            (originalPlateBox[2] / originalWidth) * videoCanvas.width,
            (originalPlateBox[3] / originalHeight) * videoCanvas.height,
          ];

          const originalCarBox = warpedBoxToRectBox(plate.car.warpedBox);
          // TODO use in click deteciton, or remove
          const convertedCarBox = [
            (originalCarBox[0] / originalWidth) * videoCanvas.width,
            (originalCarBox[1] / originalHeight) * videoCanvas.height,
            (originalCarBox[2] / originalWidth) * videoCanvas.width,
            (originalCarBox[3] / originalHeight) * videoCanvas.height,
          ];

          const frameId = frameDetection.frame_id;
          const plateText = plate.text;

          const textMeasurements = ctx.measureText(plateText);
          const textWidth = textMeasurements.width;
          const textHeight =
            textMeasurements.fontBoundingBoxAscent +
            textMeasurements.fontBoundingBoxDescent;
          const topLeft =
            clickX >= convertedPlateBox[0] &&
            clickY >= convertedPlateBox[1] - textHeight;
          const bottomRight =
            clickX <= convertedPlateBox[0] + convertedPlateBox[2] + textWidth &&
            clickY <= convertedPlateBox[1] + convertedPlateBox[3];

          return {
            topLeft,
            bottomRight,
            plateText,
            frameId,
            originalPlateBox,
          };
        })
        .filter(({ topLeft, bottomRight }) => topLeft && bottomRight) || [];

    if (detectedPlates.length > 0) {
      setPlaying(false);
    } else {
      setPlaying(!playing);
    }
    detectedPlates.forEach(
      ({ topLeft, bottomRight, plateText, frameId, originalPlateBox }) => {
        if (topLeft && bottomRight) {
          setPlaying(false);
          setOffenseModalVisible(true);
          setOffenseModalData((state) => ({
            ...state,
            frame_id: frameId,
            vehicle_id: correctLP(plateText),
          }));
          getPlateImage(...originalPlateBox);
          // getCarBodyImage(...originalCarBox);
          getFullCarBodyImage();
        }
      }
    );
  };

  function getPlateImage(x, y, width, height) {
    const plateCanvas = document.createElement("canvas");
    const plateCtx = plateCanvas.getContext("2d");
    plateCanvas.width = width;
    plateCanvas.height = height;

    plateCtx.drawImage(
      currentVideoRef.current,
      x,
      y,
      width,
      height,
      0,
      0,
      width,
      height
    );
    const plateImage = plateCanvas.toDataURL("image/jpeg", 0.99);
    setOffenseModalData((state) => ({
      ...state,
      vehicle_id_img: plateImage,
    }));
  }

  function getFullCarBodyImage() {
    const carCanvas = document.createElement("canvas");
    const carCtx = carCanvas.getContext("2d");
    carCanvas.width = adjustedVideoRect.width;
    carCanvas.height = adjustedVideoRect.height;
    carCtx.drawImage(
      currentVideoRef.current,
      0,
      0,
      adjustedVideoRect.width,
      adjustedVideoRect.height
    );
    const carBodyImage = carCanvas.toDataURL("image/jpeg", 0.99);
    setOffenseModalData((state) => ({ ...state, vehicle_img: carBodyImage }));
  }

  useLayoutEffect(() => {
    if (videoCanvasRef.current) {
      const { width, height } = adjustedVideoRect;
      videoCanvasRef.current.width = width;
      videoCanvasRef.current.height = height;

      if (width < 769) {
        currentVideoRef.current?.setAttribute("controls", "controls");
      } else {
        currentVideoRef.current?.removeAttribute("controls");
      }
    }
  }, [adjustedVideoRect.width, adjustedVideoRect.height]);

  const handleHideCropper = () => {
    dispatch(clearImageCropper());
  };

  useEffect(() => {
    let handle = null;
    const _updateCanvas = (_, { mediaTime }) => {
      handle = updateCanvas(
        mediaTime,
        setCurrentPlayerTime,
        currentVideoRef,
        currentDetectionsData,
        videoCanvasRef,
        currentLastFrameNumber,
        _updateCanvas
      );
    };

    handle = currentVideoRef.current.requestVideoFrameCallback(_updateCanvas);

    // small hack, that will force video to render
    currentVideoRef.current.currentTime =
      currentVideoRef.current.currentTime + 0.0001;
    return () => {
      handle && currentVideoRef.current.cancelVideoFrameCallback(handle);
    };
  }, [
    setCurrentPlayerTime,
    currentVideoRef,
    currentVideoRef.current,
    currentDetectionsData,
    videoCanvasRef,
    currentLastFrameNumber,
  ]);

  const creatorClient = reportContext.report?.creator_client || null;
  const encodingRequired =
    creatorClient !== null ? creatorClient.encoding_required : true;
  const videoRefCb = useCallback(
    (ref) => {
      reportContext.videoRef.current = ref;
      videoMeasureRef(ref);
    },
    [reportContext.videoRef]
  );

  const extraVideoRefCb = useCallback(
    (ref) => {
      reportContext.extraVideoRef.current = ref;
      extraVideoMeasureRef(ref);
    },
    [reportContext.extraVideoRef]
  );
  return (
    <>
      <StyledComponent
        style={{
          padding: 0,
          // maxWidth: videoDetectionData.width,
          margin: "auto",
          boxShadow: "none",
        }}
      >
        <div className="video-wrapper">
          <Tabs
            activeKey={tab}
            onChange={setTab}
            animated={{ inkBar: true, tabPane: false }}
            className="display-none"
          >
            <Tabs.TabPane key="video" forceRender>
              <video
                crossOrigin="anonymous"
                style={{
                  width: "100%",
                  maxHeight: 750,
                  background: "#000",
                }}
                onPlay={() => {
                  setPlaying(true);
                }}
                onPause={() => setPlaying(false)}
                src={videoUrl}
                ref={videoRefCb}
              />
            </Tabs.TabPane>
            <Tabs.TabPane key="extra_video" forceRender>
              <video
                crossOrigin="anonymous"
                style={{
                  width: "100%",
                  maxHeight: 750,
                  background: "#000",
                }}
                onPlay={() => {
                  setPlaying(true);
                }}
                onPause={() => setPlaying(false)}
                ref={extraVideoRefCb}
                src={extraVideoUrl}
              />
            </Tabs.TabPane>
          </Tabs>
          <canvas
            ref={videoCanvasRef}
            onClick={(event) => handleClick(event)}
            style={{ display: detectionsVisible ? "block" : "none" }}
          />
          <Player className="video-controler" ref={containerRef}>
            <CropperPopupSlider
              imageType={imageType}
              isActive={isActive}
              isCropperActive={isCropperActive}
              handleVideoSliderChanges={handleVideoSliderChanges}
              playerTimeInPercents={playerTimeInPercents}
              playerTime={currentPlayerTime}
              containerRef={containerRef}
              handleHideCropper={handleHideCropper}
              vehicle_id={vehicle_id}
              setPlaying={setPlaying}
              handleForward={handleForward}
            />
            <div className="player-buttons">
              {!playing ? (
                <button
                  onClick={() => {
                    setPlaying(true);
                  }}
                >
                  <AiOutlinePlaySquare />
                </button>
              ) : (
                <button
                  onClick={() => {
                    setPlaying(false);
                  }}
                >
                  <AiOutlinePause />
                </button>
              )}
              <div>
                {moment.utc(currentPlayerTime * 1000).format("mm:ss")}
                {" / "}
                {moment
                  .utc((currentVideoRef.current?.duration || 0) * 1000)
                  .format("mm:ss")}
              </div>
              <div className="fast-forward">
                <Tooltip title={t("0.6 sekund ortga qaytarish")}>
                  <button
                    onClick={() => handleBackward(0.6)}
                    className="fast-forward-buttons"
                  >
                    <IoIosArrowBack />
                    <IoIosArrowBack />
                  </button>
                </Tooltip>
                <Tooltip title={t("30 milisekund ortga qaytarish")}>
                  <button onClick={() => handleBackward(0.03)}>
                    <IoIosArrowBack />
                  </button>
                </Tooltip>
                <Tooltip title={t("30 milisekund oldiga o'tish")}>
                  <button onClick={() => handleForward(0.03)}>
                    <IoIosArrowForward />
                  </button>
                </Tooltip>
                <Tooltip title={t("0.60 sekund oldiga o'tish")}>
                  <button
                    onClick={() => handleForward(0.6)}
                    className="fast-forward-buttons"
                  >
                    <IoIosArrowForward />
                    <IoIosArrowForward />
                  </button>
                </Tooltip>
              </div>
              <div className="video-list">
                <Tooltip title={t("DRBni avtomatik aniqlash")}>
                  <Switch
                    checkedChildren={t("Yoqilgan")}
                    unCheckedChildren={t("O'chiq")}
                    onChange={(checked) => setDetectionsVisible(checked)}
                    checked={detectionsVisible}
                    size="default"
                  />
                </Tooltip>

                <a href={currentDownloadUrl} download>
                  <AiOutlineDownload size={30} />
                </a>
                <button
                  onClick={() => {
                    setExtraPlaying(false);
                    reportContext.extraVideoRef.current.pause();
                    setTab("video");
                  }}
                  className={`video-list-btn ${
                    tab === "video" ? "active" : ""
                  }`}
                >
                  <AiOutlineVideoCamera />
                  {t("Video")} 1
                </button>
                {hasExtraVideo ? (
                  <Tooltip title={reportContext.report.extra_video_type}>
                    <button
                      onClick={() => {
                        setVideoPlaying(false);
                        reportContext.videoRef.current.pause();
                        setTab("extra_video");
                      }}
                      className={`video-list-btn ${
                        tab === "extra_video" ? "active" : ""
                      }`}
                    >
                      <AiOutlineVideoCamera />
                      {t("Video")} 2
                    </button>
                  </Tooltip>
                ) : null}
              </div>
            </div>
          </Player>
        </div>
        <VideoStatus
          encodingRequired={encodingRequired}
          encoder_status={currentEncoderStatus}
          detector_status={currentDetectorStatus}
          isExtraVideo={tab === "extra_video"}
          reportId={reportContext.report.id}
        />

        {offenseModalVisible ? (
          <Suspense fallback={<Spin />}>
            <OffenseModal
              // TODO use extraData
              hideModal={() => {
                setOffenseModalVisible(false);
                dispatch(clearImageCropper());
              }}
              extraData={offenseModalData}
              visible={offenseModalVisible}
            />
          </Suspense>
        ) : null}
      </StyledComponent>
      {isCropperActive ? (
        <CropImage
          source={currentVideoRef.current}
          sourceHeight={currentVideoRef.current.videoHeight}
          sourceWidth={currentVideoRef.current.videoWidth}
          boundingBox={adjustedVideoRect}
          isActive={isActive}
          isCropperActive={isCropperActive}
        />
      ) : null}
    </>
  );
}
