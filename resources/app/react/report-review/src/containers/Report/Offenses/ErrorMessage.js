import React from 'react';
import { StyledErrors } from '../style';
import t from '../../../lang'


export default function ErrorMessag({ errors, fieldName }) {
  if (!errors[fieldName]) {
    return null
  }

  return (
    <StyledErrors className="has-error error-message">
      {t(errors[fieldName].value)}
    </StyledErrors>
  )
}
