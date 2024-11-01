import React, { useState } from "react";
import t from "../../lang";
import { AiOutlineCheckCircle } from "react-icons/ai";
import { VscError } from "react-icons/vsc";
import { BsArrowCounterclockwise } from "react-icons/bs";
import { Tooltip, Tag, Button } from "antd";
import { useMemo } from "react";
import axios from "../../utils/axios";

export default function VideoStatus({
  encodingRequired,
  encoder_status,
  detector_status,
  isExtraVideo,
  reportId,
}) {
  const [isEncoderSubmitted, setIsEncoderSubmitted] = useState(false);
  const [isDetectionSubmitted, setIsDetectionSubmitted] = useState(false);

  const handleEncode = async () => {
    const videoSourType = isExtraVideo ? "extra_video" : "video";
    try {
      const { data } = await axios({
        url: `/staff/reports/${reportId}/${videoSourType}/encode`,
        method: "POST",
      });

      if (data) {
        console.log(data);
        setIsEncoderSubmitted(true);
      }
    } catch (err) {
      console.log(err);
    }
  };

  const handleDetect = async () => {
    const videoSourType = isExtraVideo ? "extra_video" : "video";
    try {
      const { data } = await axios({
        url: `/staff/reports/${reportId}/${videoSourType}/detect`,
        method: "POST",
      });

      if (data) {
        console.log(data);
        setIsDetectionSubmitted(true);
      }
    } catch (err) {
      console.log(err);
    }
  };

  const encodingStatus = useMemo(() => {
    if (
      isEncoderSubmitted ||
      encoder_status === "created" ||
      encoder_status === "started"
    ) {
      return (
        <Tag color="processing" icon={<BsArrowCounterclockwise />}>
          {t("Jarayonda")}
        </Tag>
      );
    }

    if (encoder_status === "finished" || !encodingRequired) {
      return (
        <Tag color="success" icon={<AiOutlineCheckCircle />}>
          {t("Yakunlangan")}
        </Tag>
      );
    }

    return (
      <Tag color="error" icon={<VscError />}>
        {t("Hatolik")}
      </Tag>
    );
  }, [encoder_status, isEncoderSubmitted]);

  const detectionStatus = useMemo(() => {
    if (isDetectionSubmitted || detector_status === "created") {
      return (
        <Tag color="processing" icon={<BsArrowCounterclockwise />}>
          {t("Jarayonda")}
        </Tag>
      );
    } else if (detector_status === "succeeded") {
      return (
        <Tag color="success" icon={<AiOutlineCheckCircle />}>
          {t("Yakunlangan")}
        </Tag>
      );
    } else if (detector_status === null) {
      return (
        <Tag color="processing" icon={<BsArrowCounterclockwise />}>
          {t("В ожидании")}
        </Tag>
      );
    }
    return (
      <Tag color="error" icon={<VscError />}>
        {t("Hatolik")}
      </Tag>
    );
  }, [detector_status, isDetectionSubmitted]);

  const enableDetectButton =
    detector_status === "failed" ||
    (encoder_status === "failed" && detector_status === null);
  return (
    <div className="video__status">
      <div className="video__status-item" htmlFor="">
        <Tooltip title={t("Videoni kodirovka holati.")}>
          {t("Kodirovka")}:
        </Tooltip>
        {encodingStatus}

        <Button
          type="dashed"
          disabled={encoder_status !== "failed"}
          onClick={handleEncode}
        >
          {t("Kodirovkalash")}
        </Button>
      </div>

      <div className="video__status-item" htmlFor="">
        <Tooltip
          title={t("Davlat raqamini avtomatik aniqlash jarayonining holati")}
        >
          {t("Davlar raqamini aniqlash")}:
        </Tooltip>
        {detectionStatus}
        <Button
          type="dashed"
          onClick={handleDetect}
          disabled={!enableDetectButton}
        >
          {t("Qayta aniqlash")}
        </Button>
      </div>
    </div>
  );
}
