import {
  AiOutlineZoomIn,
  AiOutlineZoomOut,
  AiOutlineRotateLeft,
  AiOutlineRotateRight,
} from "react-icons/ai";
import React, {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";
import { Button, Space } from "antd";
import t from "../../lang";
import { useDispatch } from "react-redux";
import {
  hideImageCropper,
  selectImage,
} from "../../store/offenseImage/actions";
import { useSelector } from "react-redux";
import Cropper from "react-cropper";
import "cropperjs/dist/cropper.css";

// TODO REFACTOR
export default function CropImage({
  source,
  sourceHeight,
  sourceWidth,
  boundingBox,
  isActive,
  isCropperActive,
}) {
  const { x, y, left, top, width, height } = boundingBox;
  const dispatch = useDispatch();
  const { imageType, vehicle_id, id } = useSelector(
    (state) => state.offenseImage || {}
  );

  useEffect(() => {
    if (isCropperActive) {
      source.scrollIntoView({
        block: "center",
        behavior: "smooth",
      });
    }
  }, [isCropperActive]);
  const [cropper, setCropper] = useState();

  const cropRef = useRef();

  const src = useMemo(() => {
    if (cropRef.current && isCropperActive) {
      const canvas = document.createElement("canvas");
      canvas.height = source.videoHeight;
      canvas.width = source.videoWidth;
      canvas
        .getContext("2d")
        .drawImage(source, 0, 0, canvas.width, canvas.height);
      return canvas.toDataURL("image/jpeg");
    }
  }, [isCropperActive, source, cropRef.current]);

  const handleCancel = () => {
    dispatch(hideImageCropper());
  };

  const handleSaveImage = () => {
    const vehicleImage = cropper?.getCroppedCanvas().toDataURL("image/jpeg");
    dispatch(
      selectImage({
        data:
          imageType === "vehicle_id_img"
            ? { vehicle_id_img: vehicleImage }
            : { vehicle_img: vehicleImage },
      })
    );
    document.getElementById(`offense_${id}`).scrollIntoView({
      block: "center",
      behavior: "smooth",
    });
  };
  const escFunction = useCallback((event) => {
    if (event.keyCode === 27) {
      handleCancel();
    }
  }, []);

  useEffect(() => {
    document.addEventListener("keydown", escFunction, false);

    return () => {
      document.removeEventListener("keydown", escFunction, false);
    };
  }, []);
  const { x: offsetX, y: offsetY } = source.getBoundingClientRect();
  return (
    isCropperActive && (
      <div
        style={{
          overflow: "visible",
          left: 0,
          top: 0,
          bottom: 0,
          right: 0,
          position: "fixed",
          zIndex: 9000,
          background: "rgba(35,35,35,0.9)",
        }}
      >
        <Cropper
          onInitialized={(instance) => {
            setCropper(instance);
          }}
          src={src}
          viewMode={3}
          guides
          minCropBoxWidth={30}
          minCropBoxHeight={30}
          aspectRatio={imageType === "vehicle_id_img" ? 4 : undefined}
          autoCropArea={1}
          modal
          style={{
            overflow: "visible",
            height,
            left: x + offsetX,
            top: y + offsetY,
            width,
            position: "fixed",
            zIndex: 9999,
          }}
          ref={cropRef}
        />
        <Space
          className="action-buttons"
          style={{
            justifyContent: "end",
            left: x + offsetX,
            right: x + offsetX,
            top: y + offsetY + height + 10,
            position: "fixed",
            zIndex: 9999,
          }}
        >
          <Button
            type="primary"
            size="large"
            onClick={() => cropper.zoom(0.1)}
            icon={<AiOutlineZoomIn style={{ verticalAlign: "middle" }} />}
          />
          <Button
            type="primary"
            size="large"
            onClick={() => cropper.zoom(-0.1)}
            icon={<AiOutlineZoomOut style={{ verticalAlign: "middle" }} />}
          />
          <Button type="primary" size="large" onClick={handleSaveImage}>
            {t("Saqlash")}
          </Button>
          <Button type="dashed" size="large" onClick={handleCancel}>
            X
          </Button>
        </Space>
      </div>
    )
  );
}
