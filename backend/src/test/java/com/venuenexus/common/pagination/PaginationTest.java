package com.venuenexus.common.pagination;

import com.venuenexus.common.error.ApiException;
import com.venuenexus.common.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    private static final Set<String> ALLOWED_SORTS = Set.of("createdAt", "name", "code");

    @Test
    @DisplayName("Default pagination parameters initialize correctly")
    void defaultPagination() {
        PageParams params = PageParams.of(null, null);
        params.validate(ALLOWED_SORTS);

        assertThat(params.page()).isEqualTo(0);
        assertThat(params.size()).isEqualTo(20);
        assertThat(params.sortDirection()).isEqualTo("ASC");

        Pageable pageable = params.toPageable(ALLOWED_SORTS);
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    @DisplayName("Valid boundary values: size 1 and size 100")
    void validBoundaries() {
        PageParams minParams = PageParams.of(0, 1);
        minParams.validate(ALLOWED_SORTS);
        assertThat(minParams.size()).isEqualTo(1);

        PageParams maxParams = PageParams.of(10, 100);
        maxParams.validate(ALLOWED_SORTS);
        assertThat(maxParams.size()).isEqualTo(100);
    }

    @Test
    @DisplayName("Negative page index throws PAGINATION_INVALID")
    void negativePageThrows() {
        PageParams params = PageParams.of(-1, 20);
        assertThatThrownBy(() -> params.validate(ALLOWED_SORTS))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.PAGINATION_INVALID))
                .hasMessageContaining("Page index must be greater than or equal to 0");
    }

    @Test
    @DisplayName("Size 0 throws PAGINATION_INVALID")
    void sizeZeroThrows() {
        PageParams params = PageParams.of(0, 0);
        assertThatThrownBy(() -> params.validate(ALLOWED_SORTS))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.PAGINATION_INVALID))
                .hasMessageContaining("Page size must be between 1 and 100");
    }

    @Test
    @DisplayName("Size 101 throws PAGINATION_INVALID")
    void sizeOverMaxThrows() {
        PageParams params = PageParams.of(0, 101);
        assertThatThrownBy(() -> params.validate(ALLOWED_SORTS))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.PAGINATION_INVALID))
                .hasMessageContaining("Page size must be between 1 and 100");
    }

    @Test
    @DisplayName("Unpermitted sort field throws PAGINATION_INVALID")
    void invalidSortFieldThrows() {
        PageParams params = PageParams.of(0, 20, "unauthorized_column", "ASC");
        assertThatThrownBy(() -> params.validate(ALLOWED_SORTS))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.PAGINATION_INVALID))
                .hasMessageContaining("Sort field 'unauthorized_column' is not permitted");
    }

    @Test
    @DisplayName("Invalid sort direction throws PAGINATION_INVALID")
    void invalidSortDirectionThrows() {
        PageParams params = PageParams.of(0, 20, "createdAt", "SIDEWAYS");
        assertThatThrownBy(() -> params.validate(ALLOWED_SORTS))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.PAGINATION_INVALID))
                .hasMessageContaining("Sort direction must be either ASC or DESC");
    }

    @Test
    @DisplayName("Valid sorting converts to Spring Pageable with Sort")
    void validSortingConverts() {
        PageParams params = PageParams.of(1, 25, "createdAt", "DESC");
        Pageable pageable = params.toPageable(ALLOWED_SORTS);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(25);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("PageResponse computes totalPages correctly")
    void pageResponseEnvelope() {
        List<String> items = List.of("T01", "T02");
        PageResponse<String> response = PageResponse.of(items, 0, 20, 45);

        assertThat(response.items()).containsExactly("T01", "T02");
        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        assertThat(response.totalElements()).isEqualTo(45L);
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
