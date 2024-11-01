CREATE OR REPLACE FUNCTION clear_offense_notify_if_offense_changed() RETURNS trigger as
$BODY$
BEGIN
    IF (NEW.reject_time <> OLD.reject_time or
        NEW.accept_time <> OLD.accept_time or
        NEW.pay_time <> OLD.pay_time or
        NEW.fine_date <> OLD.fine_date or
        NEW.dismiss_time <> OLD.dismiss_time or
        NEW.forward_time <> OLD.forward_time) THEN
        NEW.creator_client_notified_at := null;
    END IF;
    RETURN NEW;
END;

$BODY$
    LANGUAGE plpgsql;

--;;

CREATE OR REPLACE FUNCTION clear_offense_notify_if_reward_changed() RETURNS trigger as
$BODY$
BEGIN
    IF (
        NEW.create_time <> OLD.create_time or NEW.pay_time <> OLD.pay_time
        ) THEN
        UPDATE offense set creator_client_notified_at = null where reward_id = NEW.id;
    END IF;
    RETURN NEW;
END;

$BODY$
    LANGUAGE plpgsql;

--;;

DROP TRIGGER offense_status_trigger ON offense;

--;;

CREATE TRIGGER offense_status_trigger
    BEFORE UPDATE
    ON offense
    FOR EACH ROW
EXECUTE PROCEDURE clear_offense_notify_if_offense_changed();

--;;

CREATE TRIGGER reward_status_trigger
    BEFORE UPDATE
    ON reward
    FOR EACH ROW
EXECUTE PROCEDURE clear_offense_notify_if_reward_changed();
