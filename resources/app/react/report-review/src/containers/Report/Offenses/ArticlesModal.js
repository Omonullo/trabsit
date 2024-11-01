import React, { useMemo, useContext } from 'react';
import { Modal, Radio } from 'antd';
import t, { getLanguage } from '../../../lang';
import ReportContext from '../../../context/ReportContext';

export default function Articles({ modalVisible, handleModal, parentRef, handleOffenses, values, }) {
  const props = useContext(ReportContext);
  const lang = getLanguage();
  const articles = useMemo(() => {
    return props.articles.map(item => {
      const label = item[`alias_${lang}`] || item.text_uz_la || item.text_uz_cy || item.text_ru;
      return ({ value: item.id, label, key: item.id, ...item })
    })
  }, [props.articles, lang]);

  return (
    <Modal
      onOk={handleModal}
      visible={modalVisible}
      onCancel={handleModal}
      title={t("Moddani tanlang")}
      footer={null}
      getContainer={() => parentRef.current}
      width={'90%'}
      style={{ paddingBottom: 40 }}
    >
      <Radio.Group
        name="article_id"
        onChange={handleOffenses}
        value={values.article_id}
        optionType="button"
        buttonStyle="solid"
        className="offenses"
      >
        {
          articles.map((item, index) => {
            const { factor, label, number, key, value } = item;
            const bg = factor >= 30 ? 'rgb(255, 135,135)' : factor >= 15 ? 'rgb(255, 195,195)' : factor >= 10 ? 'rgb(255, 215,215)' : factor >= 5 ? 'rgb(255, 235,235)' : factor >= 2 ? 'rgb(255, 247,247)' : '#fff';
            return (
              <Radio.Button
                className="radio-btn"
                value={value}
                key={key}
                style={{ backgroundColor: bg }}
              >
                <div className="article-number">
                  <span>{number}</span>
                  <div>{factor} {t('BHM')}</div>
                </div>
                <div className="article-content">
                  {label}
                </div>
              </Radio.Button>)
          })
        }
      </Radio.Group>
    </Modal>
  )
}
