package vn.edu.toeic.protocol.exam.rich;

import java.util.List;

public record RoomCatalogPage(List<Item> items, String nextAfterRoomCode) {
    public RoomCatalogPage { items=items==null?null:List.copyOf(items); }
    public record Item(String roomCode,String sessionId,String title,String state,int candidates,int joined,java.time.Instant createdAt) {
        public Item(String roomCode,String sessionId,String title,String state,int candidates,int joined){
            this(roomCode,sessionId,title,state,candidates,joined,null);
        }
    }
}
