create table revision (
    report_id uuid references report,
    invalidate_time timestamp,
    data varchar
);
