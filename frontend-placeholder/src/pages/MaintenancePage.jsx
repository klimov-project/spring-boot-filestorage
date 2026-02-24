import Container from '@mui/material/Container';
import { Box } from '@mui/material';
import Typography from '@mui/material/Typography';
import BuildIcon from '@mui/icons-material/Build';

const MaintenancePage = () => {
  return (
    <Container disableGutters>
      <Box className={'errorContainer'} sx={{ mt: 15, textAlign: 'center' }}>
        <BuildIcon sx={{ fontSize: 80, color: 'text.secondary', mb: 2 }} />
        <Typography
          variant="h1"
          sx={{ width: '100%', textAlign: 'center', marginBottom: 0 }}
        >
          503
        </Typography>
        <Typography
          variant="h3"
          style={{ width: '100%', textAlign: 'center', marginBottom: 2 }}
        >
          Maintenance Mode
        </Typography>
        <Typography
          variant="body1"
          color="text.secondary"
          sx={{ maxWidth: 500, mx: 'auto' }}
        >
          Our system is currently undergoing scheduled maintenance. Please check
          back shortly.
        </Typography>
      </Box>
    </Container>
  );
};

export default MaintenancePage;
