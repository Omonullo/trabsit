import { createStore, combineReducers, applyMiddleware } from "redux";
import thunkMiddleware from "redux-thunk";
import logger from "redux-logger";

// ----------- Reducers ---------------
import offenseImage from './offenseImage/reducer';

const rootReducer = combineReducers({
  offenseImage,
});

const middlewares = [thunkMiddleware];
if (process.env.NODE_ENV === "development") {
  middlewares.push(logger);
}

const store = createStore(
  rootReducer,
  applyMiddleware(...middlewares)
);

export default store;
