CREATE OR REPLACE FUNCTION clear_offense_notify_if_offense_changed() RETURNS trigger as
$BODY$
BEGIN
    IF (NEW.reject_time is distinct from OLD.reject_time or
        NEW.accept_time is distinct from OLD.accept_time or
        NEW.pay_time is distinct from OLD.pay_time or
        NEW.fine_date is distinct from OLD.fine_date or
        NEW.dismiss_time is distinct from OLD.dismiss_time or
        NEW.forward_time is distinct from OLD.forward_time) THEN
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
            NEW.create_time is distinct from OLD.create_time or NEW.pay_time is distinct from OLD.pay_time
        ) THEN
        UPDATE offense set creator_client_notified_at = null where reward_id = NEW.id;
    END IF;
    RETURN NEW;
END;

$BODY$
    LANGUAGE plpgsql;
