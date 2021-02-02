
const defaultTheme = {
    grid_breakpoints: {
        xs: 0,
        sm: 576,
        md: 768,
        lg: 992,
        xl: 1200
    },
    max_container_width: {
        sm: 540,
        md: 720,
        lg: 960,
        xl: 1140
    },
    column_gutter: {
        xs: 24,
        sm: 24,
        md: 24,
        lg: 24,
        xl: 24
    },
    outer_gutter: {
        xs: 24,
        sm: 24,
        md: 24,
        lg: 24,
        xl: 24
    }
}

export default {
    grid: {
        ...defaultTheme
    }
}

export const noOuterGutter = {
    grid: {
        ...defaultTheme,
        outer_gutter: {
            xs: 0,
            sm: 0,
            md: 0,
            lg: 0,
            xl: 0
        }
    }
}

export const noGutter = {
    grid: {
        ...defaultTheme,
        outer_gutter: {
            xs: 0,
            sm: 0,
            md: 0,
            lg: 0,
            xl: 0
        },
        column_gutter: {
            xs: 0,
            sm: 0,
            md: 0,
            lg: 0,
            xl: 0
        }
    }
}