-- 리포트가 조회할 업무 샘플 테이블
-- 실제 도입 시에는 기관의 업무 DB 를 바라보게 하고 이 파일은 제거한다.

drop table if exists budget_execution;
drop table if exists civil_complaint;
drop table if exists contract_award;

-- 세출 집행 내역
create table budget_execution (
    exec_id       bigint       not null,
    fiscal_year   int          not null,
    dept_code     varchar(20)  not null,
    dept_name     varchar(100) not null,
    program_name  varchar(200) not null,
    account_name  varchar(100) not null,
    exec_date     date         not null,
    vendor_name   varchar(200),
    budget_amt    numeric(18)  not null,
    exec_amt      numeric(18)  not null,
    status        varchar(20)  not null,
    primary key (exec_id)
);

create index ix_budget_dept on budget_execution (fiscal_year, dept_code, exec_date);

-- 민원 접수 및 처리
create table civil_complaint (
    complaint_no   varchar(30)  not null,
    dept_name      varchar(100) not null,
    category       varchar(50)  not null,
    title          varchar(300) not null,
    applicant_name varchar(50),
    received_date  date         not null,
    due_date       date         not null,
    completed_date date,
    handler_name   varchar(50),
    status         varchar(20)  not null,
    satisfaction   int,
    primary key (complaint_no)
);

create index ix_complaint_dept on civil_complaint (dept_name, received_date);

-- 계약 체결 내역
create table contract_award (
    contract_no   varchar(30)  not null,
    dept_name     varchar(100) not null,
    method        varchar(30)  not null,
    contract_kind varchar(20)  not null,
    subject       varchar(300) not null,
    vendor_name   varchar(200) not null,
    contract_date date         not null,
    amount        numeric(18)  not null,
    primary key (contract_no)
);

create index ix_contract_method on contract_award (method, amount);
