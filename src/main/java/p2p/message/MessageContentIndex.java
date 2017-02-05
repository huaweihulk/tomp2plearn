package p2p.message;

/**
 * Describes the index of a {@code Message.Content} enum in a {@code Message}.
 * <b>Note:</b> Each {@code Message} can contain up to 8 contents, so indices range from 0 to 7.
 * 
 * @author Thomas Bocek
 *
 */
public class MessageContentIndex {
    private final int index;
    private final Message.Content content;
    public MessageContentIndex(int index, Message.Content content) {
        this.index = index;
        this.content = content;
    }
    
    /**
     * The index of the associated content.
     * 
     * @return The index of the associated content.
     */
    public int index() {
        return index;
    }
    
    /**
     * The content of the associated index.
     * 
     * @return The content of the associated index.
     */
    public Message.Content content() {
        return content;
    }
}
