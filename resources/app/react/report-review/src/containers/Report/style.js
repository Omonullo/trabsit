import styled from "styled-components";

export default styled.div`
  background-color: #fff;
  padding: 25px;
  box-shadow: 0px 0px 13px 0px rgb(82 63 105 / 5%);
  background-color: #fff;
  margin-bottom: 20px;
  border-radius: 4px;
  position: relative;

  video::-webkit-media-controls-panel {
    display: none !important;
    pointer-events: none;
    opacity: 1 !important;
  }

  .display-none .ant-tabs-nav {
    display: none;
  }
  .ant-tooltip {
    max-width: 300px;
  }
  .ant-tooltip-arrow-content {
    background-color: #fff;
    width: 10px;
    height: 10px;
    top: 3px;
  }
  .tab-icon {
    font-size: 15px;
    display: flex;
    align-items: center;

    svg {
      margin-right: 7px;
      font-size: 18px;
    }
  }

  .label {
    text-align: right;
    padding-right: 24px;
    display: flex;
    align-items: center;
    justify-content: flex-end;
  }
  .d-flex {
    display: flex;
    align-items: center;
  }
  .col-right {
    display: flex;
    flex-direction: column;

    & > div:last-of-type {
      margin-top: auto;
      margin-bottom: 10px;
    }
  }

  .selected-article {
    padding: 15px 18px;
    border: 2px solid #ebedf2;
    border-radius: 4px;
    /* width: max-content; */
    display: flex;
    align-items: center;
    justify-content: flex-start;
    color: #6c7293;
    cursor: pointer;
    background-color: #fff;

    .article-number {
      font-size: 16px;
      margin-right: 20px;
      color: #6c7293;
    }

    .factor {
      font-size: 12px;
      margin-top: -5px;
      white-space: nowrap;
    }
    p {
      margin-bottom: 0;
    }
  }
  .remove-btn {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 15px;

    button {
      background-color: rgb(233 30 99 / 16%);
      color: #e91e63;
      border: 1px solid transparent;
      border-radius: 4px;
      cursor: pointer;
      height: 28px;
      line-height: 28px;
    }
  }

  .vehicle_id.vehicle_id--flag {
    min-width: 200px;
  }

  .plate-number {
    border-radius: 5px;
    border: 1px solid;
    height: 42px;
    color: #434343;
    background: #f7f7f7;

    input {
      background: #f7f7f7;
      font-size: 18px;
      font-weight: 700;
      text-align: center;
    }

    .ant-input-suffix div {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      font-size: 10px;
      img {
        width: 20px;
      }
    }
  }

  .sub-title {
    font-size: 1.2rem;
  }

  .video-apperance {
    display: inline-flex;
    align-items: center;
    margin-right: 10px;
    background-color: #9c27b0;
    border-color: #9c27b0;
    color: #fff;

    svg {
      margin-right: 4px;
    }

    &.extra {
      background-color: initial;
      border-color: initial;
      color: initial;
    }
  }

  .save-btn {
    margin-top: 20px;
    text-align: center;
  }
  .text-red {
    color: #e91e63;
    margin-left: 15px;
  }
  .status-input {
    /* display: flex;
    flex-direction: column;  */
  }
  /* .d-flex {
    display: flex;
    align-items: flex-start;
  } */
  .flex-column {
    flex-direction: column;
    align-items: flex-start;
  }
  .mt-3 {
    margin-top: 15px;
  }
  .mt-2 {
    margin-top: 15px;
  }

  .offenses {
    display: flex;
    align-items: stretch;
    flex-wrap: wrap;
    flex-direction: column;
    max-height: 80vh;
    overflow-y: auto;
    gap: 15px;

    label {
      /* width: calc(20% - 20px); */
      /* margin: 10px; */
      height: 100%;
      min-height: 70px;
      padding: 10px;
      border-radius: 4px;
      cursor: pointer;
      width: calc(25% - 15px);
    }

    .radio-btn {
      display: flex;
      align-items: center;
      border: 2px solid #ebedf2;
      padding: 15px 18px;

      &:hover {
        color: #000;
      }
    }
    .radio-btn > span:last-of-type {
      width: 100%;
      display: flex;
      align-items: center;

      .article-number {
        margin-right: 10px;
        line-height: 1.4;
        white-space: nowrap;

        span {
          color: #366cf3;
          font-size: 18.75px;
          font-weight: 300;
        }
      }
    }
    .ant-radio-button-wrapper-checked {
      background: #5d78ff !important;
      border-color: #5d78ff;

      .article-number {
        span {
          color: #fff !important;
        }
      }
    }
  }
  .select-input {
    max-width: 600px;
    margin-bottom: 10px;
  }
  .not-duplicate {
    margin-top: -20px;
    margin-bottom: 30px;
  }
  &.offense-item {
    box-shadow: none;
    border-bottom: 1px solid #eee;
    border-top: 1px solid #eee;
    margin-bottom: 20px;

    &.rejected {
      background-color: rgba(249, 62, 62, 0.05);
      border-color: #f93e3e;
      border-radius: 0;
    }
    &.accepted {
      background-color: rgba(73, 204, 144, 0.05);
      border-color: #49cc90;
      border-radius: 0;
    }

    .not-found {
      font-weight: 600;
      color: #e91e63;
    }

    .add-btn {
      display: inline-flex;
      align-items: center;
      background-color: #007566;
      border-color: #006658;
      margin-top: 20px;
      vertical-align: bottom;

      svg {
        margin-right: 5px;
      }
    }

    .label {
      align-items: flex-start;
      &.align-items-center {
        align-items: center;
      }
    }

    .empty-image {
      display: flex;
      flex-direction: column;
      background: #fff;
      padding: 8px;
      border-radius: 4px;
      border: 1px solid #4dcc91;
      text-align: center;

      .take-image {
        margin: auto;
        margin-top: 8px;
        display: flex;
        align-items: center;

        svg {
          margin-right: 5px;
        }
      }
    }

    .empty-image > .has-error,
    &.rejected .has-error {
      background-color: #fff;
    }
    .error-message {
      font-size: 13px;
    }

    .has-error {
      color: red;
      padding: 5px 12px;
      background: rgb(255 0 0 / 5%);
      margin-bottom: 0;
      border-radius: 2px;
    }
    &.accepted .has-error {
      background: transparent;
    }
  }
  .shallow-clone {
    display: none;
  }
  .shallow-clone.active {
    display: block;
    position: fixed;
    width: 300px;
    right: 30px;
    bottom: 30px;
    z-index: 1000;
  }
  .offenses-tab {
  }

  .video-source {
    width: 100%;
  }

  .video-wrapper {
    position: relative;
    canvas {
      z-index: 6;
      position: absolute;
      left: 50%;
      top: 0;
      right: 50%;
      bottom: 0;
      transform: translateX(-50%);
      opacity: 1;
      /* width: 100%;
      height: 100%; */
    }
    .video-spinner {
      position: absolute;
      left: 0;
      top: 0;
      right: 0;
      bottom: 0;
      height: calc(100% - 90px);
      width: 100%;
      background: rgb(255 255 255 / 56%);
      display: flex;
      align-items: center;
      justify-content: center;
      z-index: 999;

      .ant-spin-lg .ant-spin-dot {
        font-size: 40px;
      }

      .ant-spin-dot-item {
        background-color: #000;
      }
    }
  }

  .status {
    background-color: #5d78ff;
    color: #fff;
    display: flex;
    justify-content: center;
    align-items: center;
    padding: 5px 10px;
    position: absolute;
    z-index: 10;
    right: -10px;
    top: 10px;

    &.player {
      top: calc(100% - 72px);
      bottom: 40px;
    }

    &.rejected,
    &.dismissed {
      background-color: #fd397a;
    }
    &.reviewed,
    &.accepted,
    &.forwarded,
    &.paid {
      background-color: #0abb87;
    }
    &.failed {
      background-color: #ffb822;
      color: black;
    }

    &.offense-item__status {
      /* top: -1px; */
    }
    &.offense-item__status.none {
      display: none;
    }
    &::before {
      content: "";
      position: absolute;
      border-style: solid;
      border-color: transparent !important;
      bottom: -10px;
      border-width: 0 0 10px 10px;
      border-left-color: #868686 !important;
      right: 0;
    }
    &.player::before {
      content: "";
      position: absolute;
      border-style: solid;
      border-color: transparent !important;
      bottom: 32px;
      border-width: 10px 0px 0px 10px;
      border-left-color: #868686 !important;
      right: 0;
    }
  }

  .article-btn {
    background: #49cc90;
    color: white;
    border: 1px solid #49cc90;
    border-radius: 3px;
  }

  .vehicle-image {
    margin-top: 20px;
    width: 100%;
    max-width: 100%;
  }

  .vehicle-images {
    display: flex;
    .d-flex {
      align-items: center;
      margin-right: 15px;
    }
    img {
      /* width: 150px; */
      height: 60px;
      object-fit: contain;
    }
  }

  .video__status {
    margin-top: 30px;
    display: flex;
    gap: 60px;
  }
  .video__status-item {
    margin-bottom: 15px;
    display: flex;
    align-items: center;
    text-transform: capitalize;

    .ant-tag {
      margin-left: 4px;
      margin-right: 25px;
      display: flex;
      align-items: center;
      gap: 5px;
      border-radius: 20px;
      padding: 1px 12px;
    }
  }

  @media (max-width: 1350px) {
    .offenses label {
      width: calc(25% - 15px);
    }
  }
  @media (max-width: 1200px) {
    .offenses label {
      width: calc(33% - 15px);
    }
  }
  @media (max-width: 1200px) {
    .offenses label {
      width: calc(50% - 15px);
    }
  }
  @media (max-width: 800px) {
    .video-controler,
    .video__status {
      display: none;
    }
  }
  @media (max-width: 768px) {
    .offenses label {
      width: calc(100% - 15px);
    }
    &.offense-item .label {
      width: 100%;
      justify-content: flex-start;
    }
    .col-left,
    .has-error {
      width: 100%;
    }
    .has-error {
      display: block;
      text-align: left;
    }
  }
`;

export const Player = styled.div`
  top: 9px;
  position: relative;
  z-index: 10;
  background-color: #191919;
  padding-bottom: 22px;

  .error-msg {
    margin-top: 15px;
  }
  .ant-slider {
    margin-left: 0;
    padding: 0;
  }
  .ant-slider:hover .ant-slider-track {
    background-color: #f44336;
  }
  .ant-slider.duration-slider {
    width: 100%;
    margin-top: -15px;
  }

  .ant-slider:hover .ant-slider-handle:not(.ant-tooltip-open) {
    border-color: #fff;
  }

  & > div .ant-tooltip-inner {
    background-color: #fff;
    color: #191919;

    .ant-tooltip-arrow-content {
      background-color: #fff;
    }
  }

  .slider-container {
    padding: 8px 3px 15px;

    .ant-slider-tooltip {
      background-color: #fff;
      position: absolute;
      top: 38px;
      width: 242px;
    }
    .close-btn {
      position: absolute;
      right: 10px;
      top: 10px;
      border: none;
      background: transparent;
      font-size: 16px;

      &:hover {
        background-color: #e4e4e4;
        color: #626262;
        border-radius: 3px;
      }
    }
    .d-flex {
      display: flex;
      align-items: center;
      /* gap: 20px; */
      justify-content: space-around;

      label {
        padding: 1px 10px;
      }

      .ant-radio-button-wrapper-checked {
        border-color: #d9d9d9;
        color: #626262;
        &:before {
          background-color: #d9d9d9;
        }
      }
      label:hover {
        background: #e4e4e4;
        border-color: #bdbdbd;
        color: #bdbdbd;

        svg {
          fill: #626262;
        }

        &:before {
          background-color: #bdbdbd;
        }
      }
    }

    button {
      display: flex;
      align-items: center;
      border-color: #a5a5a5;
    }
  }

  .ant-slider-track {
    background-color: #f44336;
    height: 6px;
  }
  .ant-slider-rail {
    height: 6px;
  }
  .ant-slider-handle {
    width: 20px;
    height: 20px;
    background-color: #f44336;
    margin-top: -8px;
  }
  .player-buttons {
    display: flex;
    align-items: center;

    button {
      background-color: transparent;
      border: none;
      margin: 5px 10px;
      display: flex;
      align-items: center;
      cursor: pointer;
    }

    svg {
      fill: #a5a5a5;
      font-size: 40px;

      &:hover {
        fill: #fff;
      }
    }

    div {
      color: #a5a5a5;

      &:hover {
        color: #fff;
      }
    }
  }

  .fast-forward {
    display: flex;
    position: relative;
  }

  .fast-forward-buttons {
    cursor: pointer;
    &:first-of-type svg:first-of-type {
      position: relative;
      right: -20px;
    }
    &:last-of-type svg:last-of-type {
      position: relative;
      left: -20px;
    }

    &:hover svg {
      fill: #fff;
    }
  }

  .video-list {
    display: flex;
    align-items: center;
    margin-left: auto;

    button {
      cursor: pointer;
      border: 1px solid transparent;
    }

    button.active,
    button:hover {
      border: 1px solid #fff;
      color: #fff;

      svg {
        fill: #fff;
      }
    }

    svg {
      margin-right: 5px;
    }
    .ant-switch {
      background: #a4a4a4;
      border-color: #fff;
      border: 1px solid #fff;
      height: 24px;
    }
    .ant-switch-checked {
      background-color: #4caf50;
    }
  }

  @media (max-width: 768px) {
    & {
      padding-bottom: 12px;
    }
    canvas {
      display: none;
    }
    .player-buttons {
      svg {
        font-size: 25px;
      }

      button {
        margin: 5px;
      }
    }
  }
`;

export const StyledOffenseModal = styled.div`
  .disabled {
    background-color: transparent;
    border-color: transparent;
    border: 1px solid #eee;
  }
  .label {
    align-items: flex-start;

    &.align-items-center {
      align-items: center;
    }
  }

  .plate-number {
    margin-top: 0;
  }

  .reference-button {
    border: 1px solid;
    border-radius: 50%;
    width: 20px;
    height: 20px;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }
  .modal__timer {
    display: flex;
    align-items: center;
    gap: 7px;
  }
  .modal__plate_image {
    display: flex;
    gap: 20px;
    margin-bottom: 15px;

    img {
      margin-bottom: 15px;
      outline: 3px dashed #ff1bd4;
      outline-offset: 5px;
      height: 60px;
      margin: 8px;
    }

    .alert-msg {
      display: flex;
      align-items: flex-start;
      /* margin-bottom: 30px; */
    }
  }
  .not-found {
    font-weight: 600;
    color: #e91e63;
  }

  .add-btn {
    display: inline-flex;
    align-items: center;
    background-color: #007566;
    border-color: #006658;
    margin-top: 20px;
    vertical-align: bottom;

    svg {
      margin-right: 5px;
    }
  }

  .add-btn.ant-popover-disabled-compatible-wrapper {
    background-color: transparent;
    border-color: transparent;

    button {
      display: flex;
      align-items: center;
    }
  }
`;

export const Confirmation = styled.div`
  .error-list {
    margin-top: 20px;

    ul {
      padding: 3px 6px;
      font-size: 12px;
    }
  }
  .add-btn {
    display: inline-flex;
    align-items: center;
    background-color: #007566;
    border-color: #006658;
    margin-top: 20px;
    vertical-align: bottom;

    svg {
      margin-right: 5px;
    }
  }
  .offenses-list {
    list-style: none;
    padding: 0;
    display: flex;
    align-items: stretch;
    justify-content: center;
    gap: 20px;
    flex-wrap: wrap;
  }
  .offense-item {
    width: 220px;
    padding: 10px;
    box-shadow: 0 0.5rem 1rem rgb(0 0 0 / 15%) !important;

    h3 {
      font-size: 18px;
      letter-spacing: 1px;
    }
    img.plate-number-img {
      width: 100%;
      max-width: 250px;
      height: 80px;
      object-fit: contain;
      margin-bottom: 7px;
    }
    .text-right {
      text-align: right;
    }
  }

  .plate-number {
    border-radius: 5px;
    border: 1px solid;
    height: 42px;
    color: #434343;
    background: #f7f7f7;
    /* margin-top: 10px; */

    input {
      background: #f7f7f7;
      font-size: 18px;
      font-weight: 700;
      text-align: center;
    }

    .ant-input-suffix div {
      display: flex;
      align-items: center;
      justify-content: center;
      flex-direction: column;
      font-size: 10px;
      img {
        width: 20px;
      }
    }
  }

  .save-btn {
    margin-top: 20px;
    display: flex;
    align-items: center;
    margin-left: auto;
    background-color: #007566;
    border-color: #006658;

    svg {
      margin-right: 10px;
    }
  }

  .add-btn.ant-popover-disabled-compatible-wrapper {
    background-color: transparent;
    border-color: transparent;

    button {
      display: flex;
      align-items: center;
    }
  }

  .disabled {
    background-color: transparent;
    border-color: transparent;
    border: 1px solid #eee;
  }
`;

export const StyledErrors = styled.span`
  color: red;
`;

export const StyledCrop = styled.div`
  .ReactCrop__rule-of-thirds-vt::before,
  .ReactCrop__rule-of-thirds-vt::after,
  .ReactCrop__rule-of-thirds-hz::before,
  .ReactCrop__rule-of-thirds-hz::after {
    background-color: transparent;
    border-top: 1px dashed white;
    border-right: 1px dashed #fff;
  }
  .ReactCrop__crop-selection {
    box-shadow: 0 0 0 9999em rgb(0 0 0 / 83%);
  }
  .action-buttons {
    position: absolute;
    right: 0;
    bottom: -50px;
    width: max-content;

    button {
      margin-left: 15px;
    }
  }

  .spinner-container {
    position: fixed;
    left: 0;
    right: 0;
    top: 0;
    bottom: 0;
    width: 100%;
    height: 100%;
    background-color: rgba(255, 255, 255, 0.685);
    display: flex;
    align-items: center;
    justify-content: center;
    z-index: 2000;
  }
`;

export const StyledImageView = styled.div`
  .control-button {
    position: absolute;
    z-index: 2;
  }
  .left-button {
    left: 20px;
  }
  .right-button {
    right: 20px;
  }
`;
