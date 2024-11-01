import styled, { createGlobalStyle } from 'styled-components';



const S = {};


S.GlobalStyle = createGlobalStyle`
  img.SRLImage {
    width: 60%;
  }
  .SRLImageZoomed {
    /* transform: scale(3); */
    width: 100vw !important;
  }
  .lightbox-container {
    header  {
      height: 80px;
      background: #090909;
      z-index: 3;
      display: flex;
      align-items: center;
      justify-content: flex-end;
      font-size: 22px;
      color: #fff;

      button {
        margin-left: 25px;
        margin-right: 30px;
        position: relative;
        background-color: transparent;
        cursor: pointer;
        border: none;
      }
      button::before {
        content: '';
        position: absolute;
        width: 2px;
        height: 40px;
        top: 0;
        left: -12px;
        background: #fff;
      }
    }
    img {
      cursor: grab;
    }
  }
  .control-button {
    position: absolute;
    z-index: 2;
    display: flex;
    align-items: center;
    padding: 6px;
    font-size: 40px;
    background-color: transparent;
    border: none;
    cursor: pointer;
    color: #fff;
    outline: none;
  }
  .left-button {
    left: 20px;
  }
  .next-button {
    right: 20px;
  }
`;

S.Wrapper = styled.section`
  background-color: #f9f9fc;
  min-height: 100vh;
  padding: 40px;
  color: #646c9a;
  font-family: Helvetica, sans-serif;

  .error-msg {
    margin-top: 20px;
    margin-bottom: 30px;
  }
  
  .text-center {
    text-align: center;
  }
  .text-right {
    text-align: right;
  }

  .d-flex {
    display: flex;
  }

  .clickable {
    cursor: pointer;
  }
  
  @media (max-width: 768px) {
    & {
      padding: 40px 10px;
    }
  }
`;

S.Container = styled.div`
  max-width: ${props => props.width || 1380}px;
  margin: auto;
  
  .divider {
    margin: 24px 0;
  }
  
  .content-wrapper {
    label {
      font-weight: 500;
      color: #ccc;
      margin-bottom: 0;
    }
    p {
      color: #424242;
    }
    input, .select-input {
      max-width: 250px;
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

  .disabled {
    background-color: transparent;
    border-color: transparent;
    border: 1px solid #eee;
  }
`;

export default S;
