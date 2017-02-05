package p2p.message;

import trunk.social.p2p.storage.Data;

public interface DataFilter {

	Data filter(Data data, boolean isConvertMeta, boolean isReply);

}
