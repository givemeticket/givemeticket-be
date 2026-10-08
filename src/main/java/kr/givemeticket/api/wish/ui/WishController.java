package kr.givemeticket.api.wish.ui;

import kr.givemeticket.api.global.auth.annotation.LoginUserId;
import kr.givemeticket.api.global.log.BusinessLogging;
import kr.givemeticket.api.wish.application.WishService;
import kr.givemeticket.api.wish.ui.apiSpec.WishApiSpec;
import kr.givemeticket.api.wish.ui.dto.response.WishStatusResponsePart;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class WishController implements WishApiSpec {

    private final WishService wishService;

    @Override
    @BusinessLogging("찜하기")
    @PutMapping("campaigns/{campaignId}/wish")
    public ResponseEntity<WishStatusResponsePart> wish(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        return ResponseEntity.ok(WishStatusResponsePart.from(wishService.wish(userId, campaignId)));
    }

    @Override
    @BusinessLogging("찜 해제")
    @DeleteMapping("campaigns/{campaignId}/wish")
    public ResponseEntity<WishStatusResponsePart> unwish(
            @LoginUserId Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        return ResponseEntity.ok(WishStatusResponsePart.from(wishService.unwish(userId, campaignId)));
    }

    @Override
    @GetMapping("campaigns/{campaignId}/wish")
    public ResponseEntity<WishStatusResponsePart> readWishStatus(
            @LoginUserId(required = false) Long userId,
            @PathVariable("campaignId") Long campaignId
    ) {
        return ResponseEntity.ok(
                WishStatusResponsePart.from(wishService.getWishStatus(campaignId, userId)));
    }
}
