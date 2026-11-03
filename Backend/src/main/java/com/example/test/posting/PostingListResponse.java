package com.example.test.posting;

import java.util.List;

public record PostingListResponse(int page,
                                  int size,
                                  long total,
                                  List<PostingItem> items){
}
