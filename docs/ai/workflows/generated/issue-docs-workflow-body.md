## 📝 작업 내용

- `docs/ai/SHARED.md`: `gh --body-file`에 GitHub 템플릿 파일 직접 사용 금지 규칙 추가
- `docs/ai/workflows/auto-pr.md`: Auto-PR 에이전트 워크플로우 전면 재작성 (금지 사항, 템플릿 참조 경로, 단계별 절차 구체화)
- `docs/ai/workflows/templates/`: `issue-feature-body.md` · `pr-body.md` 골격 파일 신규 추가
- `docs/ai/workflows/generated/`: PR·이슈 본문 생성 결과물 디렉터리 신규 추가
- `docs/erd/erd-design.md`: Markdown 테이블 정렬 포맷 정비 (내용 변경 없음)

## Definition of Done (DoD)

- [ ] 핵심 비즈니스 로직에 대한 테스트 코드를 작성했는가?
- [ ] 동시성 테스트를 통과했는가? (재고·주문·결제 경로 해당 시)
- [x] SSOT 문서(api-spec, invariants, erd-design 등)를 동시 갱신했는가?
- [ ] RestDocs API 문서를 업데이트했는가? (API 추가·수정 시)
- [x] 로컬 환경에서 엔드투엔드 시나리오를 직접 확인했는가?
- [x] 소스코드 내 민감 정보(API Key, 패스워드 등)가 없는가?

## 📅 마감 기한

- 2026-05-22

## Related

-