export default {
  logo: <span style={{ fontWeight: 700 }}>📈 증권사 개발·CS 지식 베이스</span>,
  project: {
    link: '',
  },
  search: {
    placeholder: '용어·내용 검색 (예: 평단가, 코루틴, T+2)',
  },
  sidebar: {
    defaultMenuCollapseLevel: 1,
    toggleButton: true,
  },
  toc: {
    title: '이 페이지 목차',
    backToTop: true,
  },
  navigation: {
    prev: true,
    next: true,
  },
  darkMode: true,
  footer: {
    content: (
      <span>
        증권사 개발·CS 지식 베이스 — 증권 도메인과 백엔드 CS 지식을 함께 정리하는 Kotlin 기반 지식 베이스.
      </span>
    ),
  },
  editLink: {
    component: null,
  },
  feedback: {
    content: null,
  },
  docsRepositoryBase: '',
  i18n: [],
  head: (
    <>
      <meta name="viewport" content="width=device-width, initial-scale=1.0" />
      <meta name="description" content="투자 경험만 있는 입문자를 위한 증권사 백엔드 개발(Kotlin) 마스터 코스" />
    </>
  ),
}
