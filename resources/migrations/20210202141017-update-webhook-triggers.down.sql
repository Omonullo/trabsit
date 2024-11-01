DROP TRIGGER offense_status_trigger ON offense;

--;;

CREATE TRIGGER offense_status_trigger
    BEFORE UPDATE
    ON offense
    FOR EACH ROW
EXECUTE PROCEDURE clear_notify_if_status_changed();

--;;

DROP TRIGGER reward_status_trigger ON reward;

--;;

DROP FUNCTION clear_offense_notify_if_offense_changed;

--;;

DROP FUNCTION clear_offense_notify_if_reward_changed;
