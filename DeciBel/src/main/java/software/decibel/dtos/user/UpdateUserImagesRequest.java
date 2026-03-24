package software.decibel.dtos.user;

import org.springframework.web.multipart.MultipartFile;

import software.decibel.customValidation.ValidImageFile;

public record UpdateUserImagesRequest(
        @ValidImageFile
        MultipartFile profilePic,
        @ValidImageFile
        MultipartFile coverPic) {

}
