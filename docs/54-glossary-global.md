# 54. 해외주식·외환 심화 용어집

해외주식 및 외환(FX) 서비스를 담당하는 백엔드 개발자가 반드시 숙지해야 할 도메인 용어를 망라한 심화 용어집이다. 거래소·시장 구조부터 주문 유형, 환전 메커니즘, 세금·규제, 글로벌 인프라까지 5개 섹션으로 구성하였으며, 각 항목마다 실무 개발 포인트를 함께 정리하였다.

---

## 해외 거래소·시장

| 용어 | 영문/약어 | 뜻 | 개발 포인트 |
|------|-----------|-----|-------------|
| 뉴욕증권거래소 | NYSE (New York Stock Exchange) | 세계 최대 규모의 미국 주식 거래소. 상장 요건이 엄격하고 대형 우량주 중심 | 마켓 코드 `XNYS` 사용. 개장·휴장일 캘린더 별도 관리 |
| 나스닥 | NASDAQ | 전자 거래 기반 미국 주식 시장. 기술주 중심 | 마켓 코드 `XNAS`. NYSE와 거래시간 동일하나 시스템 구조 상이 |
| 아멕스 | AMEX (American Stock Exchange) | 현재 NYSE American으로 통합. ETF·소형주 다수 상장 | 티커 중복 가능성 있으므로 거래소 코드 함께 저장 필요 |
| 도쿄증권거래소 | TSE / JPX (Tokyo Stock Exchange) | 아시아 최대 거래소 중 하나. 2022년 프라임·스탠다드·그로스로 재편 | 시차 UTC+9. 점심 휴장(11:30~12:30) 처리 필요 |
| 홍콩거래소 | HKEX (Hong Kong Exchanges and Clearing) | 홍콩달러(HKD) 결제. 중국 본토 기업 H주 다수 | 랏(Lot) 단위 거래. 1랏 = 종목마다 상이, 마스터 데이터 필수 |
| 상해거래소 | SSE (Shanghai Stock Exchange) | 중국 본토 A주(위안화). 후강퉁 통해 외국인 접근 가능 | Stock Connect 쿼타(일별 한도) API 연동 필요 |
| 심천거래소 | SZSE (Shenzhen Stock Exchange) | 중소·기술주 중심 본토 거래소. 선강퉁 통해 외국인 접근 | 선강퉁 적격 종목 리스트 주기적 업데이트 |
| 후강퉁 | Shanghai-Hong Kong Stock Connect | 홍콩-상해 교차 매매 제도 | 북향(홍콩→상해)/남향(상해→홍콩) 쿼타 분리 관리 |
| 선강퉁 | Shenzhen-Hong Kong Stock Connect | 홍콩-심천 교차 매매 제도 | 후강퉁과 쿼타 독립. 적격 종목 코드 세트 별도 유지 |
| 유럽거래소 | Euronext | 파리·암스테르담·브뤼셀·리스본 등 통합 거래소 | 복수 통화(EUR) 처리, 국가별 세금 상이 |
| 프리마켓 | Pre-Market / Extended Hours | 미국 정규장(09:30~16:00 ET) 이전 시간외 거래(04:00~09:30 ET) | 호가 스프레드 크고 유동성 낮음. 별도 세션 플래그 처리 |
| 정규장 | Regular Session | NYSE·NASDAQ 기준 09:30~16:00 ET | DST 적용 시 UTC 오프셋 변경 자동 반영 로직 필요 |
| 애프터마켓 | After-Market / After-Hours | 정규장 종료 후 거래(16:00~20:00 ET) | 체결 가능 주문 유형 제한. LOO/MOC 불가 |
| 서머타임 | DST (Daylight Saving Time) | 미국 기준 3월~11월 EDT(UTC-4), 나머지 EST(UTC-5) | DB에 IANA 타임존(`America/New_York`) 저장 권장. 매년 전환일 자동 처리 |
| 거래시간대·시차 | Market Hours / Time Zone | 거래소별 현지 시간대 기준 개장·폐장 시각 | 모든 시각 UTC로 저장, 화면 표시 시 현지 변환 |
| 휴장일 | Market Holiday | 거래소별 공휴일로 거래 중단되는 날 | 외부 캘린더 API(예: Quandl, 거래소 공식) 또는 자체 테이블 관리. 연간 미리 적재 |
| 티커 | Ticker / Ticker Symbol | 거래소에서 종목을 식별하는 알파벳 약칭(예: AAPL, 005930) | 거래소 코드와 복합키 구성. 글로벌 환경에서 단독 티커는 고유성 보장 안 됨 |
| CUSIP | CUSIP (Committee on Uniform Security Identification Procedures) | 미국·캐나다 증권 9자리 식별 코드 | 사용 시 라이선스 비용 발생 가능. ISIN 변환 테이블 보유 권장 |
| ISIN | ISIN (International Securities Identification Number) | 국제 표준 12자리 증권 식별 코드(ISO 6166) | 글로벌 통합 마스터 DB 키로 사용. 국가코드(2)+식별코드(9)+체크디짓(1) |
| ADR | ADR (American Depositary Receipt) | 미국 시장에서 외국 주식을 달러로 거래할 수 있도록 발행한 예탁증서 | 1 ADR = N주 비율(ratio) 마스터 관리. 배당·분할 이벤트 별도 처리 |
| GDR | GDR (Global Depositary Receipt) | ADR의 글로벌 버전. 복수 거래소 동시 상장 | ADR과 동일 구조. 결제 통화·거래소별 처리 분기 |
| 우선주 | Preferred Stock | 보통주 대비 배당 우선권, 의결권 제한. 티커에 `.PR` 등 접미사 | 보통주와 별도 종목 코드로 관리. 배당 이벤트 처리 필요 |
| 소수점 거래 | Fractional Share Trading | 1주 미만 소수점 단위 거래(예: 0.001주) | 수량 필드를 정수 대신 DECIMAL(18,6) 이상으로 설계. 결제 금액 반올림 정책 명확화 |
| 보통주 | Common Stock / Ordinary Share | 의결권과 배당권을 가진 일반 주식 | 종목 유형 코드(CS/PS/ETF 등) 분류 체계 설계 |
| ETF | Exchange-Traded Fund | 거래소 상장 펀드. 주식처럼 실시간 매매 | 운용사 제공 실시간 iNAV(추정순자산가치) 연동 고려 |

---

## 해외 매매·주문

| 용어 | 영문/약어 | 뜻 | 개발 포인트 |
|------|-----------|-----|-------------|
| 해외주문 | Overseas Order / Cross-Border Order | 국내 고객이 해외 거래소에서 집행하는 매매 주문 | 브로커-딜러(현지 파트너사) 연동 FIX 프로토콜 또는 REST API 설계 |
| 원화주문서비스 | KRW Order Service | 환전 없이 원화로 해외주식을 주문하면 자동 환전 후 체결하는 서비스 | 환전 시점(주문 시 vs 체결 시) 및 환율 적용 정책 명확화 필요 |
| 통합증거금 | Integrated Margin / Unified Margin | 원화·외화 자산을 통합하여 증거금으로 활용하는 제도 | 통화별 환산율 실시간 적용. 증거금 부족 판단 로직 복잡도 증가 |
| 외화증거금 | Foreign Currency Margin | 특정 외화(USD, HKD 등)를 증거금으로 사용 | 통화별 증거금 계좌 분리 관리. 부족 시 자동 환전 여부 설정 |
| LOO 주문 | LOO (Limit-On-Open) | 개장 시 지정가로 집행되는 주문. 미체결 시 자동 취소 | 개장 세션 시작 직전 주문 전송. 거래소별 수락 마감 시각 관리 |
| MOO 주문 | MOO (Market-On-Open) | 개장 시 시장가로 집행되는 주문 | 가격 없이 수량만 전달. 슬리피지 위험 고객 고지 필요 |
| LOC 주문 | LOC (Limit-On-Close) | 장 마감 시 지정가로 집행. 미체결 시 취소 | 마감 경매(Closing Auction) 참여. 거래소별 수락 가능 시간 상이 |
| MOC 주문 | MOC (Market-On-Close) | 장 마감 시 시장가로 집행 | NYSE 기준 15:45 ET 이후 신규·취소 제한. 처리 마감 타이머 구현 |
| 데이 주문 | Day Order | 당일 정규장 종료까지 유효한 주문 | 장 종료 시 자동 취소 처리 배치 또는 이벤트 트리거 필요 |
| GTC 주문 | GTC (Good-Till-Cancelled) | 취소 전까지 유효한 주문. 통상 30~90일 한도(예시·확인필요) | 만료일 관리 및 고객 통보 로직. 거래소별 최대 유효 기간 상이 |
| 미국주식 결제 | T+1 Settlement (US Equities) | 미국주식 체결 후 1영업일 뒤 결제(2024년 5월 시행, 예시·확인필요) | 결제일 산정 시 미국 영업일 캘린더 적용. 기존 T+2 로직 마이그레이션 필요 |
| 예약주문 | Pre-Order / Scheduled Order | 장 시작 전 미리 등록해 두는 주문 | 주문 유효성(잔고·환율) 장 개시 시점 재검증 로직 필요 |
| 환전주문 | FX Order / Currency Conversion Order | 통화 교환을 목적으로 하는 주문 | 환전 전용 주문 유형 분리. 수수료·스프레드 적용 후 수령액 사전 안내 |
| 자동환전 | Auto FX / Auto Currency Exchange | 해외주식 매수 시 원화 잔고를 자동으로 외화로 환전하는 기능 | 환전 시점 환율 스냅샷 보관(감사 추적). 환전 실패 시 주문 롤백 처리 |
| 정산환율 | Settlement FX Rate | 결제일 기준으로 적용되는 최종 환율 | 체결환율과 정산환율 이원화 관리. 차이 발생 시 차액 정산 |
| 체결환율 | Executed FX Rate | 실제 주문 체결 시점에 적용된 환율 | 체결 통보 메시지에 환율 포함하여 원화 환산 체결금액 계산 |
| 분할매수 | Dollar-Cost Averaging (DCA) / Regular Investment | 일정 금액을 주기적으로 분할 매수하는 전략 주문 | 스케줄러 기반 반복 주문 생성. 각 회차 환율·가격 별도 기록 |
| 재매수 제한 | Pattern Day Trader (PDT) Rule | 미국 기준 마진계좌에서 5일 내 4회 이상 당일 매수·매도 시 제한(25,000달러 미만, 예시·확인필요) | PDT 카운터 실시간 집계. 한도 도달 시 주문 거부 및 고객 안내 |
| 숏셀링 | Short Selling | 보유하지 않은 주식을 빌려 매도한 뒤 낮은 가격에 재매수하여 차익을 얻는 전략 | 대주(대여) 가능 여부 조회 API 연동. 국가별 공매도 규제 반영 |

---

## 외환·환전

| 용어 | 영문/약어 | 뜻 | 개발 포인트 |
|------|-----------|-----|-------------|
| 환율 | Exchange Rate (FX Rate) | 두 통화 간 교환 비율 | 실시간 마켓 데이터(예: Reuters Eikon, Bloomberg) 수신 및 캐싱 전략 |
| 매매기준율 | Basic Exchange Rate | 서울 외환시장에서 형성된 기준 환율. 국내 금융기관 공시의 기준이 됨 | 한국은행·서울외환시장 공시 API 연동(영업일 오전 고시) |
| 현찰환율 | Cash Exchange Rate | 실물 외화 지폐 거래 시 적용 환율 | 해외주식 서비스에서는 미적용이 일반적. 구분 코드 관리 |
| 전신환환율 | TT Rate (Telegraphic Transfer Rate) | 전신 송금 시 적용되는 환율. 현찰환율보다 스프레드 좁음 | 해외주식 외화 결제에 주로 적용. 매입/매도 구분 |
| 매입환율 | Buying Rate | 금융기관이 고객으로부터 외화를 사들일 때 적용하는 환율 | 외화 입금·환전 시 적용. 매매기준율 – 스프레드 |
| 매도환율 | Selling Rate | 금융기관이 고객에게 외화를 팔 때 적용하는 환율 | 외화 출금·환전 시 적용. 매매기준율 + 스프레드 |
| 스프레드 | FX Spread | 매입환율과 매도환율의 차이. 금융기관의 수익 포함 | 스프레드 정책 테이블 관리. 우대 적용 시 동적 계산 |
| 환전수수료 | FX Commission / Conversion Fee | 환전 거래 시 부과하는 수수료 | 스프레드와 별도 수수료 이원화 또는 통합 여부 정책 결정 |
| 환전우대 | FX Discount / FX Preferential Rate | 우량 고객 또는 이벤트 대상에게 스프레드를 할인해 주는 혜택 | 우대율(%) 정책 테이블. 고객 등급·채널별 차등 적용 로직 |
| 외화예수금 | Foreign Currency Deposit | 고객 계좌에 보유한 외화(USD, JPY 등) 잔액 | 통화별 예수금 계좌 분리. 이자 계산 시 통화별 금리 적용 |
| 원화예수금 | KRW Deposit | 원화 기준 고객 예수금 잔액 | 해외주식 원화주문 서비스에서 출발점. 가환전 전 잔고 검증 |
| 가환전 | Provisional FX Conversion / Pre-FX | 주문 접수 시점에 임시로 환율을 적용해 원화 잔고를 차감하는 처리 | 체결 후 정산환율로 재정산. 차액 환불 또는 추가 차감 로직 구현 |
| 재환전 | Reverse FX / Reconversion | 보유 외화를 다시 원화로 환전하는 행위 | 매입환율 적용. 환차익·환차손 손익 계산 및 과세 처리 |
| 환차익 | FX Gain / Foreign Exchange Gain | 환율 변동으로 발생한 이익 | 보유 기간 환율 변동분 계산. 세금 처리(해외주식 양도세와 합산 여부 확인필요) |
| 환차손 | FX Loss / Foreign Exchange Loss | 환율 변동으로 발생한 손실 | 손익 통산 계산 시 환차손 포함 여부 정책 확인필요 |
| 환헤지 | FX Hedge | 환율 변동 위험을 줄이기 위한 파생상품(선물환·옵션) 활용 전략 | 고객 대상 헤지 서비스 제공 시 파생 계약 관리 모듈 필요 |
| 선물환 | Forward Exchange / FX Forward | 미래 특정 시점의 환율을 현재 고정하는 계약 | 만기일 관리, 롤오버 처리, NDF(Non-Deliverable Forward) 구분 |
| 통합증거금 환산 | Margin FX Conversion | 다통화 잔고를 기준 통화(KRW 또는 USD)로 환산하여 증거금 산정 | 환산 시 적용 환율(기준율 vs 실시간) 정책 명확화. 환산 오차 누적 관리 |
| 결제통화 | Settlement Currency | 실제 결제가 이루어지는 통화(예: 미국주식 → USD) | 거래소별 결제통화 마스터 관리. 복수 통화 결제 지원 설계 |
| NDF | NDF (Non-Deliverable Forward) | 만기 시 실물 인도 없이 차액만 정산하는 선물환. 원화처럼 역외 거래 제한 통화에 활용 | 차액 정산 기준 환율(Fixing Rate) 확인. ISDA 계약 기반 처리 |
| 크로스환율 | Cross Rate | 기축통화(USD) 경유 없이 두 비달러 통화 간 환율을 계산한 값 | 직접 호가 없는 통화 쌍 환산 시 삼각 계산 로직 구현 |
| 핍 | Pip (Percentage in Point) | 환율의 최소 변동 단위. 대부분 소수점 넷째 자리(0.0001) | 환율 정밀도(소수점 자릿수) DB 컬럼 설계 시 DECIMAL(18,6) 권장 |

---

## 세금·규제(해외)

| 용어 | 영문/약어 | 뜻 | 개발 포인트 |
|------|-----------|-----|-------------|
| 해외주식 양도소득세 | Capital Gains Tax on Overseas Stocks | 해외주식 매도 차익에 부과하는 세금. 연 250만원 기본공제(예시·확인필요), 세율 22%(지방세 포함, 예시·확인필요) | 연간 손익 누계 계산 모듈. 기본공제 차감 후 과세표준 산출 |
| 배당소득세(현지원천징수) | Withholding Tax on Dividends | 배당 지급 시 현지 국가에서 원천징수하는 세금. 미국 기준 15%(예시·확인필요) | 배당 입금 시 원천징수 금액 자동 차감 및 명세 제공 |
| W-8BEN | W-8BEN Form | 미국 세법상 비거주 외국인임을 증명하는 IRS 양식. 조세조약 혜택 적용 | 서명·갱신(3년, 예시·확인필요) 주기 관리. 미제출 시 30% 원천징수(예시·확인필요) |
| 이중과세방지협정 | Tax Treaty / Double Taxation Agreement (DTA) | 두 국가 간 동일 소득에 대해 이중과세를 방지하는 협약 | 국가별 조약 세율 테이블 관리. W-8BEN 등록 여부와 연계 |
| 외국납부세액공제 | Foreign Tax Credit | 해외에서 납부한 세금을 국내 세금에서 공제받는 제도 | 고객별 외국 납부세액 누계 관리. 종합소득세 신고 자료 제공 |
| FBAR | FBAR (Foreign Bank and Financial Accounts Report) | 미국 납세자 의무 해외금융계좌 신고(한국 서비스는 직접 해당 없으나 미국 거주 고객 대상 고려) | 해당 고객 세그먼트 식별 및 안내 문구 제공 수준으로 대응 |
| 해외금융계좌신고 | Overseas Financial Account Report | 한국 거주자의 해외 금융계좌 잔액이 연중 최고 5억원 초과 시 신고 의무(예시·확인필요) | 고객 잔고 모니터링 및 신고 안내 알림 기능. 국세청 제출 서식 생성 |
| 양도세 신고 | Capital Gains Tax Filing | 해외주식 양도소득세를 다음해 5월 종합소득세 신고 기간에 신고하는 절차(예시·확인필요) | 연간 손익 계산서(매매내역) 자동 생성 및 다운로드 기능 |
| 250만원 공제 | KRW 2.5M Exemption | 해외주식 양도소득세 연간 기본공제 한도(예시·확인필요) | 연도별 손익 누계에서 공제 적용 후 세액 계산. 변경 시 정책 테이블 업데이트 |
| 손익통산 | Profit and Loss Netting / Tax Loss Harvesting | 해외주식 간 이익과 손실을 합산하여 순이익 기준으로 과세하는 방식 | 동일 과세연도 내 전 종목 손익 집계. 국내주식과 통산 여부는 불가(예시·확인필요) |
| 원천징수 | Withholding | 소득 지급 시 지급자가 세금을 미리 차감하여 납부하는 제도 | 배당·이자 지급 이벤트 수신 시 자동 차감 처리. 명세서 보관 |
| 세금우대 | Tax-Exempt / Tax-Advantaged Account | 특정 계좌(ISA 등)에서 세금 혜택을 제공하는 제도 | 계좌 유형별 세금 계산 분기. ISA 해외주식 편입 한도 관리(예시·확인필요) |
| 간이세액표 | Simplified Tax Table | 원천징수세액 산출 시 간이 기준으로 사용하는 세액 테이블 | 배당 간이 세율 적용 시 사용. 정기 개정 반영 필요 |
| 국가별 세율 | Country-Specific Tax Rate | 배당·이자 소득에 대한 국가별 원천징수 세율 | 국가코드 + 소득유형별 세율 테이블 관리. 조세조약 우대세율 별도 컬럼 |
| 세금영수증 | Tax Receipt / Tax Statement | 원천징수 내역을 증명하는 서류 | 연간 세금내역서 자동 생성(PDF). 고객 다운로드 및 이메일 발송 기능 |
| 손실이월 | Tax Loss Carryforward | 당해 연도 손실을 다음 연도로 이월하여 세금 경감에 활용(국가별 가능 여부 상이, 예시·확인필요) | 연도별 손익 이월 관리. 한국 해외주식의 경우 이월 불가 정책 확인필요 |

---

## 글로벌 기타

| 용어 | 영문/약어 | 뜻 | 개발 포인트 |
|------|-----------|-----|-------------|
| 글로벌 예탁기관(커스터디) | Global Custodian / Custody Bank | 해외 증권의 실물 보관 및 결제를 대행하는 기관(예: BNY Mellon, Citibank) | 커스터디 API(FIX, SWIFT, 독점 REST) 연동. 잔고 정합성 주기적 대사 |
| 보관기관 | Sub-Custodian / Local Custodian | 현지 시장에서 글로벌 커스터디를 보조하는 현지 기관 | 다단계 커스터디 체인에서 잔고 추적. 장애 시 대체 경로 설계 |
| 명의(스트리트 네임) | Street Name | 투자자 실명이 아닌 브로커·커스터디 명의로 증권을 등록하는 방식 | 실질 소유자(Beneficial Owner) 별도 관리. 기업행위 시 소유자 매핑 |
| 의결권 행사 | Proxy Voting | 주주총회에서 의결권을 행사하는 권리. 해외주식은 커스터디 경유 | 의결권 행사 의향 수집 UI 및 커스터디 전달 로직. 마감일 알림 |
| 기업행위(해외배당) | Corporate Action - Cash Dividend | 해외 기업의 현금배당. 외화로 지급 후 원화 환산 또는 외화 입금 | 배당락일(Ex-Date), 기준일(Record Date), 지급일(Pay Date) 3단계 이벤트 관리 |
| 기업행위(액면분할) | Corporate Action - Stock Split | 주식 수를 늘리고 주가를 비례 감소. 보유 수량·단가 자동 조정 | 분할 비율(ratio) 수신 후 보유 수량 및 평균 단가 일괄 업데이트 배치 |
| 기업행위(합병·인수) | Corporate Action - Merger & Acquisition | 기업 합병 시 주식 교환 또는 현금 지급 | 교환 비율에 따른 보유 종목 자동 전환. 고객 안내 및 선택권 제공 로직 |
| 기업행위(무상증자) | Corporate Action - Bonus Issue / Stock Dividend | 이익잉여금을 자본금으로 전환하여 무상으로 주식 지급 | 배정 비율 기반 수량 증가 처리. 커스터디 통보 후 잔고 반영 |
| 기업행위(권리락) | Corporate Action - Rights Issue | 기존 주주에게 신주 인수권을 부여하는 유상증자 | 권리 행사 기간 관리 및 고객 의향 수집. 미행사 시 권리 소멸 처리 |
| 시세료 | Market Data Fee | 거래소 실시간 시세 사용에 대한 라이선스 비용 | 개인/전문투자자 구분 약관 동의 관리. 미동의 고객에게 지연 시세 제공 |
| 실시간 시세 | Real-Time Quote | 거래소에서 실시간으로 제공하는 호가·체결 데이터 | WebSocket 또는 고속 메시지 브로커(Kafka) 활용. 라이선스 계약 후 배포 |
| 지연 시세 | Delayed Quote | 실시간 시세 대비 15~20분(예시·확인필요) 지연 제공 데이터 | 무료 제공 가능. 지연 분 수 메타데이터 화면 표시 |
| 환노출 | FX Exposure / Currency Exposure | 환율 변동에 따른 손익 위험이 발생하는 포지션 | 통화별 환노출 금액 집계. 헤지 적용 후 순 노출 계산 |
| 표준결제일 | Standard Settlement Date | 체결 후 자금·증권이 실제 이동하는 날짜(T+1, T+2 등) | 거래소·상품별 결제주기 마스터. 결제일 도래 시 자금 이동 트리거 |
| 네팅 | Netting | 동일 결제일 내 복수 거래의 매수·매도 금액을 상계하여 순액으로 결제 | 커스터디와 네팅 합의 여부 계약 확인. 건별 vs 순액 결제 로직 분기 |
| SWIFT | SWIFT (Society for Worldwide Interbank Financial Telecommunication) | 국제 금융 메시지 표준 네트워크. 해외 송금·결제에 활용 | BIC 코드 관리. MT103(송금), MT202(은행간 이체) 메시지 파싱 |
| FIX 프로토콜 | FIX (Financial Information eXchange) Protocol | 증권 주문·체결 정보 전달을 위한 국제 표준 메시지 프로토콜 | FIX 4.2/4.4/5.0 버전 호환성 확인. QuickFIX 등 오픈소스 엔진 활용 |
| 대사(리콘) | Reconciliation (Recon) | 내부 시스템 잔고와 커스터디·거래소 잔고를 대조·검증하는 작업 | 일별 자동 대사 배치. 불일치 건 알림 및 수동 조정 워크플로 |
| 청산 | Clearing | 체결된 거래의 채무·채권을 공식 확정하고 상계하는 과정 | 중앙청산소(CCP) 연동. 청산 실패(Failed Settlement) 모니터링 |
| 결제 | Settlement | 청산된 거래의 증권과 자금을 실제 이전하는 최종 단계 | 결제 실패 시 바이인(Buy-In) 또는 연장 처리. STP(직선 처리) 목표 설계 |
| 수익자 | Beneficial Owner | 증권의 법적 명의와 무관하게 실질 경제적 이익을 갖는 자 | KYC/AML 목적으로 수익자 정보 별도 관리. 규제 보고 시 활용 |
| KYC | KYC (Know Your Customer) | 금융기관의 고객 신원 확인 의무 | 해외주식 계좌 개설 시 본인 인증, 투자 성향, 자금 출처 수집 플로 구현 |
| AML | AML (Anti-Money Laundering) | 자금세탁 방지 규제 및 모니터링 체계 | 이상거래탐지시스템(FDS) 연동. 임계 금액 초과 거래 자동 보고 |
| LEI | LEI (Legal Entity Identifier) | 법인 고객을 글로벌 표준으로 식별하는 20자리 코드 | MiFID II 등 유럽 규제 보고 시 필수. 법인 고객 마스터에 저장 |

---

[전체 용어집](09-glossary.md) · [전체 커리큘럼](../CURRICULUM.md)
