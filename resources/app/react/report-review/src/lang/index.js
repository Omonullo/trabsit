import uz_la from '../lang/uz_la.json';
import uz_cy from '../lang/uz_cy.json';
import ru from '../lang/ru.json';
const dictionary = {
  ru,
  uz_la,
  uz_cy,
}

function getLanguage() {
  const locale = document.cookie.split('; ')?.find(item => item.startsWith('locale'));
  return locale?.split('=')?.[1] || 'uz_la'
}

function t(str) {
  const lang = getLanguage();
  if (dictionary[lang][str]) {
    return dictionary[lang][str]
  }
  return str;
}

export { t as default, getLanguage };