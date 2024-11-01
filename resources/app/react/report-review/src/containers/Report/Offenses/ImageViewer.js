import React, { useState, useMemo } from 'react';
import { useEffect } from 'react';
import { IoIosArrowForward, IoIosArrowBack } from 'react-icons/io';
import Lightbox from 'react-spring-lightbox';

const CoolLightbox = ({ images = [], activeImageIndex = 0, isOpen = false, handleClose }) => {
  const imagesList = useMemo(() => images.filter(i => i.src), [images]);
  const [currentImageIndex, setCurrentIndex] = useState(activeImageIndex);
  useEffect(() => {
    setCurrentIndex(activeImageIndex);
  }, [activeImageIndex]);

  const gotoPrevious = () =>
    currentImageIndex > 0 && setCurrentIndex(currentImageIndex - 1);

  const gotoNext = () =>
    currentImageIndex + 1 < imagesList.length &&
    setCurrentIndex(currentImageIndex + 1);

  return (

    <Lightbox
      isOpen={isOpen}
      onPrev={gotoPrevious}
      onClose={handleClose}
      onNext={gotoNext}
      images={imagesList}
      currentIndex={currentImageIndex}
      /* Add your own UI */
      renderHeader={() => (<Header handleClose={handleClose} currentIndex={currentImageIndex + 1} total={imagesList.length} />)}
      // renderFooter={() => (<CustomFooter />)}
      renderPrevButton={() => <PrevArrowButton gotoPrevious={gotoPrevious} />}
      renderNextButton={() => <NextArrowButton gotoNext={gotoNext} />}
      // renderImageOverlay={() => (<ImageOverlayComponent >)}

      /* Add styling */
      // className="cool-class"
      style={{ background: "rgb(0 0 0 / 80%)" }}

      /* Handle closing */
      // onClose={handleClose}

      /* Use single or double click to zoom */
      singleClickToZoom

    /* react-spring config for open/close animation */
    // pageTransitionConfig={{
    //   from: { transform: "scale(0.75)", opacity: 0 },
    //   enter: { transform: "scale(1)", opacity: 1 },
    //   leave: { transform: "scale(0.75)", opacity: 0 },
    //   config: { mass: 1, tension: 320, friction: 32 }
    // }}
    />

  );
};

function Header({ handleClose, currentIndex, total }) {
  return (
    <header>
      <div className="count-wrapper">
        {currentIndex}/{total}
      </div>
      <button onClick={handleClose}>X</button>
    </header>
  )
}

function PrevArrowButton({ gotoPrevious }) {
  return (
    <button onClick={gotoPrevious} className="control-button left-button">
      <IoIosArrowBack />
    </button>
  )
}

function NextArrowButton({ gotoNext }) {
  return (
    <button onClick={gotoNext} className="control-button next-button">
      <IoIosArrowForward />
    </button>
  )
}

export default CoolLightbox;
