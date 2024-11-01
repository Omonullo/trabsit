update offense
set asbt_log = substring(asbt_log from length(asbt_log) - position(E'\n\n' in reverse(asbt_log)) + 2)
where asbt_log is not null and position(E'\n\n' in asbt_log) > 0;
